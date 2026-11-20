package com.duyikang.rental.module.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.constant.ItemStatus;
import com.duyikang.rental.common.constant.OrderStatus;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.common.lock.RedisLock;
import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.module.item.entity.Category;
import com.duyikang.rental.module.item.entity.Community;
import com.duyikang.rental.module.item.entity.Item;
import com.duyikang.rental.module.item.mapper.CategoryMapper;
import com.duyikang.rental.module.item.mapper.CommunityMapper;
import com.duyikang.rental.module.item.mapper.ItemMapper;
import com.duyikang.rental.module.order.dto.OrderCreateDTO;
import com.duyikang.rental.module.order.dto.OrderQuoteDTO;
import com.duyikang.rental.module.order.entity.RentalOrder;
import com.duyikang.rental.module.order.mapper.OrderOccupancyMapper;
import com.duyikang.rental.module.order.mapper.RentalOrderMapper;
import com.duyikang.rental.module.order.service.OrderService;
import com.duyikang.rental.module.order.service.OrderTxService;
import com.duyikang.rental.module.order.vo.OrderCreatedVO;
import com.duyikang.rental.module.order.vo.OrderDetailVO;
import com.duyikang.rental.module.order.vo.OrderListItemVO;
import com.duyikang.rental.module.order.vo.OrderQuoteVO;
import com.duyikang.rental.module.system.entity.SysConfig;
import com.duyikang.rental.module.system.mapper.SysConfigMapper;
import com.duyikang.rental.module.user.entity.User;
import com.duyikang.rental.module.user.mapper.UserMapper;
import com.duyikang.rental.security.CurrentUser;
import com.duyikang.rental.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 订单服务实现（K2 三层防线的**编排者**）。
 *
 * <pre>
 * 第一层：应用层预检 —— 参数、可见性、自租、状态、日期、冲突（便宜，失败快）
 * 第二层：Redis 锁 lock:item:{itemId} —— 把同一物品的并发下单串行化，抢不到 500ms 后判 4090
 * 第三层：order_occupancy 唯一索引 —— 真正的正确性保障，锁失效时兜底
 * </pre>
 *
 * <p>本类**不标** {@code @Transactional}：锁必须在事务外面，事务交给 {@link OrderTxService}。
 * 详见 {@code PROJECT_RULES.md} §8.3 与 {@code OrderTxService} 的类注释。</p>
 *
 * <p>错误码口径：抢锁失败不许漏成 5003，一律 {@link ErrorCode#OCCUPANCY_CONFLICT}（4090），
 * 压测断言"成功 1 单、其余全 4090、无 5003/5000"就靠这条。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    /** 锁名与 TTL：TTL 必须大于一次下单的最长耗时，否则锁会提前消失 */
    private static final String LOCK_KEY_PREFIX = "lock:item:";
    private static final long LOCK_TTL_SECONDS = 10L;
    /** 抢锁重试：500ms 内每 50ms 一次，共 10 次。不无限重试——那会耗尽 Tomcat 线程池 */
    private static final int LOCK_RETRY_TIMES = 10;
    private static final long LOCK_RETRY_INTERVAL_MS = 50L;

    private static final String MAX_DAYS_KEY = "order.max_days";
    private static final int DEFAULT_MAX_DAYS = 30;
    /** 候选时段：最多给 3 个，向后最多扫 60 天 */
    private static final int SUGGEST_LIMIT = 3;
    private static final int SUGGEST_SCAN_DAYS = 60;
    /** 分页：与全局口径一致（pageNum 从 1 起、pageSize 默认 10 上限 100） */
    private static final int PAGE_SIZE_DEFAULT = 10;
    private static final int PAGE_SIZE_MAX = 100;

    private final ItemMapper itemMapper;
    private final RentalOrderMapper rentalOrderMapper;
    private final OrderOccupancyMapper orderOccupancyMapper;
    private final CategoryMapper categoryMapper;
    private final CommunityMapper communityMapper;
    private final UserMapper userMapper;
    private final SysConfigMapper sysConfigMapper;
    private final RedisLock redisLock;
    private final OrderTxService orderTxService;

    // ------------------------------------------------------------------ 试算

    @Override
    public OrderQuoteVO quote(OrderQuoteDTO dto) {
        LoginUser user = CurrentUser.require();
        Item item = requireRentableItem(dto.itemId(), user, dto.startDate(), dto.endDate());
        int rentDays = rentDays(dto.startDate(), dto.endDate());

        List<LocalDate> occupied = orderOccupancyMapper.selectOccupiedDates(item.getId(), dto.startDate(), dto.endDate());
        if (!occupied.isEmpty()) {
            throw conflictException(item.getId(), occupied, dto.startDate(), rentDays);
        }

        BigDecimal rentAmount = rentAmount(item.getDailyPrice(), rentDays);
        return new OrderQuoteVO(item.getId(), dto.startDate(), dto.endDate(), rentDays,
                item.getDailyPrice(), item.getDeposit(), item.getOverdueRate(),
                rentAmount, item.getDeposit(), rentAmount.add(item.getDeposit()));
    }

    // ------------------------------------------------------------------ 下单

    @Override
    public OrderCreatedVO create(OrderCreateDTO dto) {
        LoginUser user = CurrentUser.require();
        Item item = requireRentableItem(dto.itemId(), user, dto.startDate(), dto.endDate());
        int rentDays = rentDays(dto.startDate(), dto.endDate());

        String lockKey = LOCK_KEY_PREFIX + item.getId();
        String lockValue = UUID.randomUUID().toString().replace("-", "");
        boolean locked = tryLock(lockKey, lockValue);
        if (!locked) {
            // 锁竞争失败也是"这一时段拿不到"，对外语义与冲突一致，不能漏成 5003
            log.warn("抢锁失败（500ms 内 {} 次）：itemId={} renterId={} {}~{}",
                    LOCK_RETRY_TIMES, item.getId(), user.userId(), dto.startDate(), dto.endDate());
            throw lockBusyException(item.getId(), dto.startDate(), dto.endDate(), rentDays);
        }

        try {
            // 锁内再查一次：把"锁外排队期间别人已经下单成功"的情况挡在插入之前
            List<LocalDate> occupied = orderOccupancyMapper.selectOccupiedDates(item.getId(), dto.startDate(), dto.endDate());
            if (!occupied.isEmpty()) {
                throw conflictException(item.getId(), occupied, dto.startDate(), rentDays);
            }

            Long orderId;
            try {
                orderId = orderTxService.createInTx(item, user.userId(), dto.startDate(), dto.endDate(),
                        rentDays, dto.remark());
            } catch (DuplicateKeyException e) {
                // 事务已回滚；唯一索引冲突 = 并发后手，翻译成 4090 并带上冲突日期
                String detail = String.valueOf(e.getMessage());
                if (detail.contains("uk_occupancy")) {
                    log.warn("唯一索引拦下并发下单：itemId={} {}~{}", item.getId(), dto.startDate(), dto.endDate());
                    List<LocalDate> conflicts = orderOccupancyMapper.selectOccupiedDates(
                            item.getId(), dto.startDate(), dto.endDate());
                    throw conflictException(item.getId(), conflicts, dto.startDate(), rentDays);
                }
                throw e;
            }

            RentalOrder saved = rentalOrderMapper.selectById(orderId);
            BigDecimal rentAmount = saved.getRentAmount() == null
                    ? rentAmount(item.getDailyPrice(), rentDays) : saved.getRentAmount();
            return new OrderCreatedVO(saved.getId(), saved.getOrderNo(), saved.getStatus(),
                    OrderStatus.labelOf(saved.getStatus()), saved.getStartDate(), saved.getEndDate(),
                    saved.getRentDays(), rentAmount, saved.getDeposit(), rentAmount.add(saved.getDeposit()));
        } finally {
            redisLock.unlock(lockKey, lockValue);
        }
    }

    // ------------------------------------------------------------------ 详情

    @Override
    public OrderDetailVO detail(Long orderId) {
        LoginUser user = CurrentUser.require();
        RentalOrder order = rentalOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        boolean bilateral = order.getRenterId().equals(user.userId()) || order.getOwnerId().equals(user.userId());
        if (!bilateral && !CurrentUser.isAdmin()) {
            // 订单确实存在，是"不该给你看"，语义与 3004（不存在）分开
            throw new BizException(ErrorCode.PERMISSION_DENIED_FOR_RESOURCE, "无权查看该订单");
        }

        Item item = itemMapper.selectById(order.getItemId());
        Category category = item == null ? null : categoryMapper.selectById(item.getCategoryId());
        Community community = item == null ? null : communityMapper.selectById(item.getCommunityId());
        User owner = userMapper.selectById(order.getOwnerId());
        User renter = userMapper.selectById(order.getRenterId());

        BigDecimal rentAmount = order.getRentAmount() == null ? BigDecimal.ZERO : order.getRentAmount();
        BigDecimal deposit = order.getDeposit() == null ? BigDecimal.ZERO : order.getDeposit();
        return new OrderDetailVO(
                order.getId(), order.getOrderNo(), order.getStatus(), OrderStatus.labelOf(order.getStatus()),
                order.getItemId(),
                item == null ? null : item.getTitle(),
                item == null ? null : item.getCoverUrl(),
                category == null ? null : category.getName(),
                community == null ? null : community.getName(),
                order.getStartDate(), order.getEndDate(), order.getRentDays(),
                order.getDailyPrice(), deposit, order.getOverdueRate(),
                rentAmount, rentAmount.add(deposit),
                order.getRemark(),
                order.getReturnRequestedAt(),
                order.getReturnRequestedState(),
                owner == null ? null : new OrderDetailVO.Party(owner.getId(), owner.getNickname()),
                renter == null ? null : new OrderDetailVO.Party(renter.getId(), renter.getNickname()),
                order.getCreatedAt());
    }

    // ------------------------------------------------------------------ 我的订单列表

    @Override
    public PageResult<OrderListItemVO> list(String role, String status, Integer pageNum, Integer pageSize) {
        LoginUser user = CurrentUser.require();
        long page = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? PAGE_SIZE_DEFAULT
                : Math.min(pageSize, PAGE_SIZE_MAX);

        LambdaQueryWrapper<RentalOrder> wrapper = Wrappers.<RentalOrder>lambdaQuery();
        String normalizedRole = role == null ? "all" : role.trim().toLowerCase();
        switch (normalizedRole) {
            case "renter" -> wrapper.eq(RentalOrder::getRenterId, user.userId());
            case "owner" -> wrapper.eq(RentalOrder::getOwnerId, user.userId());
            default -> wrapper.and(w -> w.eq(RentalOrder::getRenterId, user.userId())
                    .or().eq(RentalOrder::getOwnerId, user.userId()));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(RentalOrder::getStatus, status.trim());
        }
        wrapper.orderByDesc(RentalOrder::getCreatedAt).orderByDesc(RentalOrder::getId);

        Page<RentalOrder> pageResult = rentalOrderMapper.selectPage(new Page<>(page, size), wrapper);
        List<RentalOrder> orders = pageResult.getRecords();
        if (orders.isEmpty()) {
            return PageResult.empty(page, size);
        }

        // 批量取物品与用户，避免列表页 N+1（一页最多 100 条）
        Map<Long, Item> itemMap = listByIds(orders.stream().map(RentalOrder::getItemId).distinct().toList(),
                itemMapper::selectBatchIds).stream().collect(Collectors.toMap(Item::getId, i -> i));
        Map<Long, User> userMap = listByIds(
                orders.stream().flatMap(o -> Stream.of(o.getOwnerId(), o.getRenterId())).distinct().toList(),
                userMapper::selectBatchIds).stream().collect(Collectors.toMap(User::getId, u -> u));

        List<OrderListItemVO> list = orders.stream().map(order -> {
            Item item = itemMap.get(order.getItemId());
            boolean asRenter = order.getRenterId().equals(user.userId());
            Long counterpartyId = asRenter ? order.getOwnerId() : order.getRenterId();
            User counterparty = userMap.get(counterpartyId);
            BigDecimal rentAmount = order.getRentAmount() == null ? BigDecimal.ZERO : order.getRentAmount();
            BigDecimal deposit = order.getDeposit() == null ? BigDecimal.ZERO : order.getDeposit();
            return new OrderListItemVO(
                    order.getId(), order.getOrderNo(), order.getItemId(),
                    item == null ? null : item.getTitle(),
                    item == null ? null : item.getCoverUrl(),
                    order.getStartDate(), order.getEndDate(), order.getRentDays(),
                    rentAmount, deposit, rentAmount.add(deposit),
                    order.getStatus(), OrderStatus.labelOf(order.getStatus()),
                    asRenter ? "renter" : "owner",
                    counterparty == null ? null : counterparty.getNickname(),
                    order.getCreatedAt());
        }).toList();

        return PageResult.of(list, pageResult.getTotal(), page, size);
    }

    private <T> List<T> listByIds(List<Long> ids, java.util.function.Function<List<Long>, List<T>> loader) {
        return ids.isEmpty() ? List.of() : loader.apply(ids);
    }

    // ------------------------------------------------------------------ 内部方法

    /**
     * 校验顺序固定：可见性 3004 → 自租 3006 → 状态 3003 → 日期 2001。
     *
     * <p>自租排在状态之前：自己的物品如果先报"状态不可租会误导，
     * 而且自租是更明确的使用错误。</p>
     */
    private Item requireRentableItem(Long itemId, LoginUser user, LocalDate startDate, LocalDate endDate) {
        Item item = itemMapper.selectById(itemId);
        boolean isOwner = item != null && item.getOwnerId().equals(user.userId());

        if (item == null) {
            throw new BizException(ErrorCode.ITEM_NOT_FOUND);
        }
        // 待审核/被驳回的物品只有发布者能看见；别人连存在性都不该知道
        if (!isOwner && (ItemStatus.PENDING.equals(item.getStatus()) || ItemStatus.REJECTED.equals(item.getStatus()))) {
            throw new BizException(ErrorCode.ITEM_NOT_FOUND);
        }
        if (isOwner) {
            throw new BizException(ErrorCode.SELF_RENT_FORBIDDEN);
        }
        if (!ItemStatus.ON_SHELF.equals(item.getStatus())) {
            throw new BizException(ErrorCode.ITEM_STATUS_INVALID);
        }
        validateDateRange(startDate, endDate);
        return item;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (!startDate.isBefore(endDate)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "归还日必须晚于起租日");
        }
        if (startDate.isBefore(LocalDate.now())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "起租日不能早于今天");
        }
        int rentDays = rentDays(startDate, endDate);
        int maxDays = maxRentDays();
        if (rentDays > maxDays) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单次租用不能超过 " + maxDays + " 天");
        }
    }

    private int rentDays(LocalDate startDate, LocalDate endDate) {
        return (int) ChronoUnit.DAYS.between(startDate, endDate);
    }

    private BigDecimal rentAmount(BigDecimal dailyPrice, int rentDays) {
        return dailyPrice.multiply(BigDecimal.valueOf(rentDays)).setScale(2, RoundingMode.HALF_UP);
    }

    private int maxRentDays() {
        SysConfig config = sysConfigMapper.selectOne(
                Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, MAX_DAYS_KEY));
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
            return DEFAULT_MAX_DAYS;
        }
        try {
            return Integer.parseInt(config.getConfigValue().trim());
        } catch (NumberFormatException e) {
            log.warn("sys_config['{}'] 不是整数（{}），回退默认值 {}",
                    MAX_DAYS_KEY, config.getConfigValue(), DEFAULT_MAX_DAYS);
            return DEFAULT_MAX_DAYS;
        }
    }

    private boolean tryLock(String lockKey, String lockValue) {
        for (int i = 0; i < LOCK_RETRY_TIMES; i++) {
            if (redisLock.tryLock(lockKey, lockValue, java.time.Duration.ofSeconds(LOCK_TTL_SECONDS))) {
                return true;
            }
            try {
                Thread.sleep(LOCK_RETRY_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private BizException conflictException(Long itemId, List<LocalDate> occupied, LocalDate startDate, int rentDays) {
        return BizException.occupancyConflict(ErrorCode.OCCUPANCY_CONFLICT.getMessage(),
                conflictData(itemId, occupied, startDate, rentDays));
    }

    private BizException lockBusyException(Long itemId, LocalDate startDate, LocalDate endDate, int rentDays) {
        List<LocalDate> occupied = orderOccupancyMapper.selectOccupiedDates(itemId, startDate, endDate);
        return BizException.occupancyConflict("该物品正在被其他用户下单，请稍后重试",
                conflictData(itemId, occupied, startDate, rentDays));
    }

    /**
     * 4090 的 data（形状已在 {@code docs/接口文档.md} §0.5 冻结）：
     * {@code {itemId, conflictDates[], suggestRanges[{startDate,endDate}]}}。
     */
    private Map<String, Object> conflictData(Long itemId, List<LocalDate> occupied,
                                             LocalDate startDate, int rentDays) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", String.valueOf(itemId));
        data.put("conflictDates", occupied.stream().map(LocalDate::toString).toList());
        data.put("suggestRanges", suggestRanges(itemId, startDate, rentDays));
        return data;
    }

    /**
     * 候选时段（规则写死在契约 §3.4）：起点 {@code max(今天, 请求 startDate)}，向后最多扫 60 天，
     * 找连续 {@code rentDays} 天全空闲的起点，最多 3 个，且候选之间不重叠。
     */
    private List<Map<String, String>> suggestRanges(Long itemId, LocalDate startDate, int rentDays) {
        LocalDate today = LocalDate.now();
        LocalDate cursor = startDate.isBefore(today) ? today : startDate;
        LocalDate scanEnd = cursor.plusDays(SUGGEST_SCAN_DAYS);

        List<LocalDate> occupied = orderOccupancyMapper.selectOccupiedDates(itemId, cursor, scanEnd.plusDays(rentDays));
        Set<LocalDate> busy = new HashSet<>(occupied);

        List<Map<String, String>> suggestions = new ArrayList<>();
        LocalDate day = cursor;
        while (day.isBefore(scanEnd) && suggestions.size() < SUGGEST_LIMIT) {
            boolean free = true;
            for (int i = 0; i < rentDays; i++) {
                if (busy.contains(day.plusDays(i))) {
                    free = false;
                    break;
                }
            }
            if (free) {
                Map<String, String> range = new LinkedHashMap<>();
                range.put("startDate", day.toString());
                range.put("endDate", day.plusDays(rentDays).toString());
                suggestions.add(range);
                day = day.plusDays(rentDays);
            } else {
                day = day.plusDays(1);
            }
        }
        return suggestions;
    }
}

