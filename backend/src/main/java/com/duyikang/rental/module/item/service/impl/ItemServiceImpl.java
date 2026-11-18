package com.duyikang.rental.module.item.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.constant.ItemStatus;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.module.credit.entity.CreditPolicy;
import com.duyikang.rental.module.item.dto.ItemQuery;
import com.duyikang.rental.module.item.dto.ItemSaveDTO;
import com.duyikang.rental.module.item.dto.ItemShelfDTO;
import com.duyikang.rental.module.item.convert.ItemConvert;
import com.duyikang.rental.module.item.entity.Category;
import com.duyikang.rental.module.item.entity.Community;
import com.duyikang.rental.module.item.entity.Item;
import com.duyikang.rental.module.item.entity.ItemImage;
import com.duyikang.rental.module.item.mapper.CategoryMapper;
import com.duyikang.rental.module.item.mapper.CommunityMapper;
import com.duyikang.rental.module.item.mapper.ItemImageMapper;
import com.duyikang.rental.module.item.mapper.ItemMapper;
import com.duyikang.rental.module.item.service.ItemService;
import com.duyikang.rental.module.item.vo.CalendarVO;
import com.duyikang.rental.module.item.vo.ItemCardVO;
import com.duyikang.rental.module.item.vo.ItemDetailVO;
import com.duyikang.rental.module.item.vo.ItemImageVO;
import com.duyikang.rental.module.item.vo.ItemOwnerVO;
import com.duyikang.rental.module.item.vo.MyItemVO;
import com.duyikang.rental.module.loss.entity.LossPolicy;
import com.duyikang.rental.module.loss.mapper.LossPolicyMapper;
import com.duyikang.rental.module.order.entity.RentalOrder;
import com.duyikang.rental.module.order.mapper.RentalOrderMapper;
import com.duyikang.rental.module.settlement.entity.FeePolicy;
import com.duyikang.rental.module.settlement.mapper.FeePolicyMapper;
import com.duyikang.rental.module.system.entity.SysConfig;
import com.duyikang.rental.module.system.mapper.SysConfigMapper;
import com.duyikang.rental.module.user.entity.User;
import com.duyikang.rental.module.user.mapper.UserMapper;
import com.duyikang.rental.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 物品模块（用户侧）实现。
 *
 * <p>几条贯穿全类的约束（PROJECT_RULES.md §0 铁律、docs/接口文档.md §2）：</p>
 * <ul>
 *   <li>所有"当前用户"都走 {@link CurrentUser}，**不接收前端传的 userId**（§7.3）。</li>
 *   <li>价格上下限、社区是否停用、类目层级都读库，不写字面量（§0-⑤）。</li>
 *   <li>列表排序在 SQL 里完成（分页必须先排序后取页，内存排序会导致翻页重复）。</li>
 *   <li>占用口径与日历、下单完全一致：只认 {@code CONFIRMED / IN_USE / OVERDUE}。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {

    /** 三张策略表都是固定 id=1 的单行表。 */
    private static final long POLICY_ID = 1L;

    /** 就近优先开关的配置键（需求总结 §5、PROJECT_RULES.md §9）。 */
    private static final String PRIORITY_KEY = "community.priority.enabled";

    /** 上传接口产出的相对路径形状：/uploads/202611/<32 位 uuid>.<ext>（FileStorageUtil 决定）。 */
    private static final Pattern IMAGE_URL_PATTERN =
            Pattern.compile("^/uploads/\\d{6}/[0-9a-f]{32}\\.(jpg|jpeg|png|webp)$");

    /** 「进行中」= 占时段的三态；PENDING 不占（需求总结 §6）。 */
    private static final List<String> ACTIVE_ORDER_STATUSES = List.of("CONFIRMED", "IN_USE", "OVERDUE");

    /** 允许编辑的状态：改完一律回到待审。 */
    private static final Set<String> EDITABLE_STATUSES =
            Set.of(ItemStatus.PENDING, ItemStatus.OFF_SHELF, ItemStatus.REJECTED);

    private static final Set<String> ALL_STATUSES = Set.of(
            ItemStatus.PENDING, ItemStatus.ON_SHELF, ItemStatus.OFF_SHELF,
            ItemStatus.RENTED, ItemStatus.MAINTAINING, ItemStatus.REJECTED);

    private static final int PAGE_SIZE_DEFAULT = 10;
    private static final int PAGE_SIZE_MAX = 100;

    /** 与 DTO 的 @Size(max=5) 保持一致：Service 层也要守住同一条上限。 */
    private static final int MAX_IMAGE_COUNT = 5;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ItemMapper itemMapper;
    private final ItemImageMapper itemImageMapper;
    private final CategoryMapper categoryMapper;
    private final CommunityMapper communityMapper;
    private final UserMapper userMapper;
    private final RentalOrderMapper rentalOrderMapper;
    private final FeePolicyMapper feePolicyMapper;
    private final LossPolicyMapper lossPolicyMapper;
    private final SysConfigMapper sysConfigMapper;

    // ------------------------------------------------------------------ 写操作

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ItemSaveDTO dto) {
        Long ownerId = CurrentUser.requireId();
        validateSaveDTO(dto);

        Item item = new Item();
        item.setOwnerId(ownerId);
        item.setCategoryId(dto.categoryId());
        item.setTitle(dto.title().trim());
        item.setDescription(blankToNull(dto.description()));
        item.setCommunityId(dto.communityId());
        item.setDailyPrice(dto.dailyPrice());
        item.setDeposit(dto.deposit());
        item.setOverdueRate(dto.overdueRate());
        item.setCoverUrl(dto.imageUrls().get(0));
        item.setLossValue(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        item.setRentCount(0);
        item.setViewCount(0);
        // 发布即待审：未审核物品不出现在检索结果里（FR-M1-02）
        item.setStatus(ItemStatus.PENDING);
        itemMapper.insert(item);

        replaceImages(item.getId(), dto.imageUrls());
        log.info("物品发布成功 itemId={} ownerId={} images={}", item.getId(), ownerId, dto.imageUrls().size());
        return item.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long itemId, ItemSaveDTO dto) {
        Item item = loadOwnedItem(itemId);
        if (!EDITABLE_STATUSES.contains(item.getStatus())) {
            throw new BizException(ErrorCode.ITEM_STATUS_INVALID,
                    "当前状态为「" + ItemStatus.labelOf(item.getStatus()) + "」，请先下架后再编辑");
        }
        validateSaveDTO(dto);

        // 用 UpdateWrapper 而不是 updateById：updateById 会跳过 null 字段，
        // 而这里必须把 auditRemark / auditAdminId / auditTime 显式置回 null（重提后清空旧审核痕迹）。
        itemMapper.update(null, Wrappers.<Item>lambdaUpdate()
                .eq(Item::getId, itemId)
                .set(Item::getTitle, dto.title().trim())
                .set(Item::getDescription, blankToNull(dto.description()))
                .set(Item::getCategoryId, dto.categoryId())
                .set(Item::getCommunityId, dto.communityId())
                .set(Item::getDailyPrice, dto.dailyPrice())
                .set(Item::getDeposit, dto.deposit())
                .set(Item::getOverdueRate, dto.overdueRate())
                .set(Item::getCoverUrl, dto.imageUrls().get(0))
                .set(Item::getStatus, ItemStatus.PENDING)
                // 清空审核痕迹必须用 setSql：UpdateWrapper#set 传 null 的生成结果依赖版本，
                // 这里要的是确定的 "= NULL"，否则驳回理由会残留到下一次提交。
                .setSql("audit_remark = NULL, audit_admin_id = NULL, audit_time = NULL"));

        replaceImages(itemId, dto.imageUrls());
        log.info("物品编辑后重新提交审核 itemId={} ownerId={}", itemId, item.getOwnerId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void shelf(Long itemId, ItemShelfDTO dto) {
        Item item = loadOwnedItem(itemId);
        if (Boolean.TRUE.equals(dto.onShelf())) {
            if (!ItemStatus.OFF_SHELF.equals(item.getStatus())) {
                throw new BizException(ErrorCode.ITEM_STATUS_INVALID,
                        "只有「已下架」的物品可以重新上架，当前状态为「" + ItemStatus.labelOf(item.getStatus()) + "」");
            }
            updateStatus(itemId, ItemStatus.ON_SHELF);
            log.info("物品上架 itemId={}", itemId);
            return;
        }
        if (!ItemStatus.ON_SHELF.equals(item.getStatus())) {
            throw new BizException(ErrorCode.ITEM_STATUS_INVALID,
                    "只有「在架」的物品可以下架，当前状态为「" + ItemStatus.labelOf(item.getStatus()) + "」");
        }
        if (hasActiveOrder(itemId)) {
            throw new BizException(ErrorCode.ITEM_STATUS_INVALID, "该物品有进行中的订单，暂不能下架");
        }
        updateStatus(itemId, ItemStatus.OFF_SHELF);
        log.info("物品下架 itemId={}", itemId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long itemId) {
        Item item = loadOwnedItem(itemId);
        if (hasActiveOrder(itemId)) {
            throw new BizException(ErrorCode.ITEM_STATUS_INVALID, "该物品有进行中的订单，不能删除");
        }
        // 逻辑删除：item_image 行保留，避免把审计线索一起删掉
        itemMapper.deleteById(itemId);
        log.info("物品逻辑删除 itemId={} ownerId={} status={}", itemId, item.getOwnerId(), item.getStatus());
    }

    // ------------------------------------------------------------------ 读操作

    @Override
    public PageResult<ItemCardVO> list(ItemQuery query) {
        checkDateRange(query.getStartDate(), query.getEndDate());
        long pageNum = normalizePageNum(query.getPageNum());
        long pageSize = normalizePageSize(query.getPageSize());
        boolean withDates = query.getStartDate() != null && query.getEndDate() != null;

        List<Long> categoryIds = resolveCategoryIds(query.getCategoryId());
        if (categoryIds != null && categoryIds.isEmpty()) {
            // 一级类目下没有二级子类目：物品只会挂在二级上，因此结果必然为空
            return PageResult.empty(pageNum, pageSize);
        }

        // 可租日期筛选与日历共用同一条占用口径，不另写区间逻辑
        List<Long> excludedIds = withDates
                ? itemMapper.selectOccupiedItemIds(query.getStartDate(), query.getEndDate())
                : null;

        boolean priorityEnabled = isCommunityPriorityEnabled();
        Long myCommunityId = null;
        String myDistrict = null;
        var loginUser = CurrentUser.optional();
        if (loginUser.isPresent()) {
            User me = userMapper.selectById(loginUser.get().userId());
            if (me != null && me.getCommunityId() != null) {
                myCommunityId = me.getCommunityId();
                Community community = communityMapper.selectById(myCommunityId);
                myDistrict = community == null ? null : community.getDistrict();
            }
        }

        Page<Item> page = itemMapper.selectForSale(
                new Page<>(pageNum, pageSize), query, categoryIds,
                myCommunityId, myDistrict, priorityEnabled, excludedIds);

        List<Item> records = page.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(List.of(), page.getTotal(), pageNum, pageSize);
        }
        Map<Long, String> categoryNames = categoryNameMap(records.stream().map(Item::getCategoryId).toList());
        Map<Long, String> communityNames = communityNameMap(records.stream().map(Item::getCommunityId).toList());
        Set<Long> occupied = withDates ? new HashSet<>(excludedIds) : Set.of();

        List<ItemCardVO> cards = new ArrayList<>(records.size());
        for (Item item : records) {
            Boolean available = withDates ? !occupied.contains(item.getId()) : null;
            cards.add(ItemConvert.toCard(item,
                    categoryNames.get(item.getCategoryId()),
                    communityNames.get(item.getCommunityId()),
                    available));
        }
        return PageResult.of(cards, page.getTotal(), pageNum, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ItemDetailVO detail(Long itemId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BizException(ErrorCode.ITEM_NOT_FOUND);
        }
        // 待审/驳回的可见性：只给归属人与管理员，其他人一律"不存在"（不泄露存在性）
        boolean restricted = ItemStatus.PENDING.equals(item.getStatus())
                || ItemStatus.REJECTED.equals(item.getStatus());
        if (restricted && !isOwnerOrAdmin(item)) {
            throw new BizException(ErrorCode.ITEM_NOT_FOUND);
        }

        // 浏览数 +1：用 setSql 让数据库自增，避免"读-改-写"并发丢更新
        itemMapper.update(null, Wrappers.<Item>lambdaUpdate()
                .eq(Item::getId, itemId)
                .setSql("view_count = view_count + 1"));
        item.setViewCount((item.getViewCount() == null ? 0 : item.getViewCount()) + 1);

        Category category = categoryMapper.selectById(item.getCategoryId());
        Community community = communityMapper.selectById(item.getCommunityId());
        User owner = userMapper.selectById(item.getOwnerId());
        Community ownerCommunity = owner == null || owner.getCommunityId() == null
                ? null : communityMapper.selectById(owner.getCommunityId());

        ItemOwnerVO ownerVO = owner == null ? null : new ItemOwnerVO(
                owner.getId(),
                owner.getNickname(),
                owner.getAvatarUrl(),
                owner.getCreditScore(),
                ownerCommunity == null ? null : ownerCommunity.getName());

        return ItemConvert.toDetail(
                item,
                category == null ? null : category.getName(),
                community == null ? null : community.getName(),
                maintenanceStateOf(item.getLossValue()),
                imageVOs(itemId),
                ownerVO);
    }

    @Override
    public CalendarVO calendar(Long itemId, String month) {
        YearMonth yearMonth = parseMonth(month);
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BizException(ErrorCode.ITEM_NOT_FOUND);
        }
        LocalDate from = yearMonth.atDay(1);
        LocalDate to = yearMonth.plusMonths(1).atDay(1);

        List<LocalDate> occupied = itemMapper.selectOccupiedDates(itemId, from, to);
        Set<LocalDate> occupiedSet = new HashSet<>(occupied);
        List<String> occupiedDates = new ArrayList<>(occupied.size());
        List<String> availableDates = new ArrayList<>();
        for (LocalDate day : occupied) {
            occupiedDates.add(day.format(DATE_FMT));
        }
        for (LocalDate day = from; day.isBefore(to); day = day.plusDays(1)) {
            if (!occupiedSet.contains(day)) {
                availableDates.add(day.format(DATE_FMT));
            }
        }
        return new CalendarVO(itemId, yearMonth.toString(), occupiedDates, availableDates);
    }

    @Override
    public PageResult<MyItemVO> mine(String status, Integer pageNum, Integer pageSize) {
        if (status != null && !status.isBlank() && !ALL_STATUSES.contains(status)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "状态取值不合法：" + status);
        }
        Long me = CurrentUser.requireId();
        long num = normalizePageNum(pageNum);
        long size = normalizePageSize(pageSize);

        Page<Item> page = itemMapper.selectPage(new Page<>(num, size),
                Wrappers.<Item>lambdaQuery()
                        .eq(Item::getOwnerId, me)
                        .eq(status != null && !status.isBlank(), Item::getStatus, status)
                        .orderByDesc(Item::getCreatedAt)
                        .orderByDesc(Item::getId));

        List<Item> records = page.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(List.of(), page.getTotal(), num, size);
        }
        List<Long> ids = records.stream().map(Item::getId).toList();
        Map<Long, String> categoryNames = categoryNameMap(records.stream().map(Item::getCategoryId).toList());
        Map<Long, String> communityNames = communityNameMap(records.stream().map(Item::getCommunityId).toList());
        Map<Long, Long> imageCounts = imageCountMap(ids);

        List<MyItemVO> list = new ArrayList<>(records.size());
        for (Item item : records) {
            int count = imageCounts.getOrDefault(item.getId(), 0L).intValue();
            list.add(ItemConvert.toMyItem(item,
                    categoryNames.get(item.getCategoryId()),
                    communityNames.get(item.getCommunityId()),
                    count));
        }
        return PageResult.of(list, page.getTotal(), num, size);
    }

    // ------------------------------------------------------------------ 校验与工具

    /** 发布/编辑共用的业务校验：类目、社区、价格区间、图片地址。 */
    private void validateSaveDTO(ItemSaveDTO dto) {
        Category category = categoryMapper.selectById(dto.categoryId());
        if (category == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "所选类目不存在");
        }
        if (category.getLevel() == null || category.getLevel() != 2) {
            throw new BizException(ErrorCode.PARAM_ERROR, "只能选择二级类目");
        }
        if (!"NORMAL".equals(category.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该类目已停用");
        }

        Community community = communityMapper.selectById(dto.communityId());
        if (community == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "所选社区不存在");
        }
        if (community.getEnabled() == null || community.getEnabled() != 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该社区已停用");
        }

        checkPriceRange(dto.dailyPrice(), dto.deposit(), dto.overdueRate());

        List<String> urls = dto.imageUrls();
        // DTO 上的 @NotEmpty/@Size(max=5) 只在 Controller 走 @Valid 时生效；
        // Service 是公共入口（定时任务、测试、将来的管理端复用），必须自己再判一次。
        if (urls == null || urls.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "至少上传 1 张物品图片");
        }
        if (urls.size() > MAX_IMAGE_COUNT) {
            throw new BizException(ErrorCode.PARAM_ERROR, "最多上传 " + MAX_IMAGE_COUNT + " 张图片");
        }
        Set<String> seen = new HashSet<>();
        for (String url : urls) {
            if (url == null || !IMAGE_URL_PATTERN.matcher(url).matches()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "图片地址不合法：" + url);
            }
            if (!seen.add(url)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "同一张图片不能重复上传");
            }
        }
    }

    /** 三条价格校验都读 fee_policy，不写字面量（改价策略只需改库）。 */
    private void checkPriceRange(BigDecimal dailyPrice, BigDecimal deposit, BigDecimal overdueRate) {
        FeePolicy policy = feePolicyMapper.selectById((int) POLICY_ID);
        if (policy == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "费用策略未初始化（fee_policy 缺少 id=1）");
        }
        if (dailyPrice.compareTo(policy.getDailyPriceMin()) < 0
                || dailyPrice.compareTo(policy.getDailyPriceMax()) > 0) {
            throw new BizException(ErrorCode.FEE_OUT_OF_RANGE,
                    "日租金需在 " + plain(policy.getDailyPriceMin()) + " ~ " + plain(policy.getDailyPriceMax()) + " 元之间");
        }
        BigDecimal minDeposit = dailyPrice.multiply(policy.getDepositRatioMin()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxDeposit = dailyPrice.multiply(policy.getDepositRatioMax()).setScale(2, RoundingMode.HALF_UP);
        if (deposit.compareTo(minDeposit) < 0 || deposit.compareTo(maxDeposit) > 0) {
            throw new BizException(ErrorCode.FEE_OUT_OF_RANGE,
                    "押金需在 " + plain(minDeposit) + " ~ " + plain(maxDeposit) + " 元之间（日租金的 "
                            + plain(policy.getDepositRatioMin()) + "~" + plain(policy.getDepositRatioMax()) + " 倍）");
        }
        if (overdueRate.compareTo(policy.getOverdueRateMin()) < 0
                || overdueRate.compareTo(policy.getOverdueRateMax()) > 0) {
            throw new BizException(ErrorCode.FEE_OUT_OF_RANGE,
                    "逾期费率需在 " + plain(policy.getOverdueRateMin()) + " ~ " + plain(policy.getOverdueRateMax()) + " 之间");
        }
    }

    /** 加载物品并校验归属人（不存在 3004，非本人 3008）。 */
    private Item loadOwnedItem(Long itemId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BizException(ErrorCode.ITEM_NOT_FOUND);
        }
        if (!item.getOwnerId().equals(CurrentUser.requireId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED_FOR_RESOURCE, "只能操作自己发布的物品");
        }
        return item;
    }

    private boolean isOwnerOrAdmin(Item item) {
        return CurrentUser.optional()
                .map(user -> user.userId().equals(item.getOwnerId()) || "ADMIN".equals(user.role()))
                .orElse(false);
    }

    /** 是否有进行中订单（决定能否下架/删除）。 */
    private boolean hasActiveOrder(Long itemId) {
        Long count = rentalOrderMapper.selectCount(Wrappers.<RentalOrder>lambdaQuery()
                .eq(RentalOrder::getItemId, itemId)
                .in(RentalOrder::getStatus, ACTIVE_ORDER_STATUSES));
        return count != null && count > 0;
    }

    /** 整体替换图片集合：先删后插，保证展示顺序 = 入参顺序（id 自增即顺序）。 */
    private void replaceImages(Long itemId, List<String> urls) {
        itemImageMapper.delete(Wrappers.<ItemImage>lambdaQuery().eq(ItemImage::getItemId, itemId));
        for (String url : urls) {
            ItemImage image = new ItemImage();
            image.setItemId(itemId);
            image.setUrl(url);
            itemImageMapper.insert(image);
        }
    }

    private void updateStatus(Long itemId, String status) {
        itemMapper.update(null, Wrappers.<Item>lambdaUpdate()
                .eq(Item::getId, itemId)
                .set(Item::getStatus, status));
    }

    private List<ItemImageVO> imageVOs(Long itemId) {
        List<ItemImage> images = itemImageMapper.selectList(Wrappers.<ItemImage>lambdaQuery()
                .eq(ItemImage::getItemId, itemId)
                .orderByAsc(ItemImage::getId));
        List<ItemImageVO> result = new ArrayList<>(images.size());
        for (ItemImage image : images) {
            result.add(new ItemImageVO(image.getId(), image.getUrl()));
        }
        return result;
    }

    private Map<Long, Long> imageCountMap(Collection<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        List<ItemImage> images = itemImageMapper.selectList(Wrappers.<ItemImage>lambdaQuery()
                .in(ItemImage::getItemId, itemIds));
        Map<Long, Long> counts = new HashMap<>();
        for (ItemImage image : images) {
            counts.merge(image.getItemId(), 1L, Long::sum);
        }
        return counts;
    }

    /** 类目筛选参数展开：一级类目 → 其全部二级子类目；二级类目 → 自身；不存在 → 空集合。 */
    private List<Long> resolveCategoryIds(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            return List.of();
        }
        if (category.getLevel() != null && category.getLevel() == 2) {
            return List.of(categoryId);
        }
        List<Category> children = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .eq(Category::getParentId, categoryId));
        return children.stream().map(Category::getId).toList();
    }

    private Map<Long, String> categoryNameMap(Collection<Long> ids) {
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return categoryMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
    }

    private Map<Long, String> communityNameMap(Collection<Long> ids) {
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return communityMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(Community::getId, Community::getName, (a, b) -> a));
    }

    /** 就近优先开关：配置缺失时按开启处理（与需求总结 §5 默认值 1 一致）。 */
    private boolean isCommunityPriorityEnabled() {
        SysConfig config = sysConfigMapper.selectOne(Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getConfigKey, PRIORITY_KEY));
        if (config == null || config.getConfigValue() == null) {
            return true;
        }
        return !"0".equals(config.getConfigValue().trim());
    }

    /** 维护状态：阈值来自 loss_policy（改阈值只需改库，不改代码）。 */
    private String maintenanceStateOf(BigDecimal lossValue) {
        if (lossValue == null) {
            return "NORMAL";
        }
        LossPolicy policy = lossPolicyMapper.selectById((int) POLICY_ID);
        if (policy == null) {
            return "NORMAL";
        }
        if (policy.getThresholdRepair() != null && lossValue.compareTo(policy.getThresholdRepair()) >= 0) {
            return "REPAIR";
        }
        if (policy.getThresholdMaintain() != null && lossValue.compareTo(policy.getThresholdMaintain()) >= 0) {
            return "MAINTAIN";
        }
        return "NORMAL";
    }

    /** 日期区间成对校验：只给一头没法判断区间，静默忽略会让前端以为筛选生效了。 */
    private void checkDateRange(LocalDate startDate, LocalDate endDate) {
        if ((startDate == null) != (endDate == null)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "startDate 与 endDate 必须同时提供");
        }
        if (startDate != null && !startDate.isBefore(endDate)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "可租起始日必须早于结束日");
        }
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            throw new BizException(ErrorCode.PARAM_FORMAT_ERROR, "month 不能为空，格式 yyyy-MM");
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_FORMAT_ERROR, "month 格式应为 yyyy-MM");
        }
    }

    private long normalizePageNum(Integer pageNum) {
        if (pageNum == null || pageNum < 1) {
            return 1L;
        }
        return pageNum;
    }

    /** pageSize 上限 100：不限的话一次拉全表，压测时先被打爆的是内存（PROJECT_RULES.md §7）。 */
    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return PAGE_SIZE_DEFAULT;
        }
        return Math.min(pageSize, PAGE_SIZE_MAX);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 去掉 BigDecimal 的尾随零，只用于拼提示文案。 */
    private static String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    /** 保留给后续切片使用：把实体集合转成 id → 实体 的索引。 */
    static <T> Map<Long, T> index(Collection<T> list, Function<T, Long> idGetter) {
        Map<Long, T> map = new HashMap<>();
        for (T element : list) {
            map.put(idGetter.apply(element), element);
        }
        return map;
    }
}
