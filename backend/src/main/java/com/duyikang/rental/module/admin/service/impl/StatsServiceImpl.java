package com.duyikang.rental.module.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.constant.ItemStatus;
import com.duyikang.rental.common.constant.OrderStatus;
import com.duyikang.rental.common.constant.SettlementStatus;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.module.admin.mapper.StatsMapper;
import com.duyikang.rental.module.admin.service.StatsService;
import com.duyikang.rental.module.admin.vo.StatsOverviewVO;
import com.duyikang.rental.module.admin.vo.StatsTrendVO;
import com.duyikang.rental.module.appeal.entity.Appeal;
import com.duyikang.rental.module.appeal.mapper.AppealMapper;
import com.duyikang.rental.module.item.entity.Item;
import com.duyikang.rental.module.item.mapper.ItemMapper;
import com.duyikang.rental.module.order.entity.RentalOrder;
import com.duyikang.rental.module.order.mapper.RentalOrderMapper;
import com.duyikang.rental.module.settlement.entity.Settlement;
import com.duyikang.rental.module.settlement.mapper.SettlementMapper;
import com.duyikang.rental.module.user.entity.User;
import com.duyikang.rental.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板统计实现。
 *
 * <p>全部是只读查询，不需要事务；计数走 Mapper 的 count，聚合走 {@code StatsMapper} 的 `SUM/GROUP BY`。
 * 日期一律服务器本地时区（`Asia/Shanghai`，见 `application.yml`），不做 UTC 转换 ——
 * 这是"按天"统计，时区差 8 小时会把昨晚的单算到前天，所以口径必须单一。</p>
 */
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final StatsMapper statsMapper;
    private final UserMapper userMapper;
    private final ItemMapper itemMapper;
    private final RentalOrderMapper rentalOrderMapper;
    private final SettlementMapper settlementMapper;
    private final AppealMapper appealMapper;

    private static final int MIN_TREND_DAYS = 1;
    private static final int MAX_TREND_DAYS = 30;
    private static final int DEFAULT_TREND_DAYS = 7;
    private static final int GMV_WINDOW_DAYS = 30;
    private static final int NEW_USER_WINDOW_DAYS = 7;
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO;

    @Override
    public StatsOverviewVO overview() {
        Map<String, Long> orderCounts = new HashMap<>();
        for (Map<String, Object> row : statsMapper.countOrdersByStatus()) {
            Object status = row.get("status");
            Object count = row.get("c");
            if (status != null && count != null) {
                orderCounts.put(String.valueOf(status), ((Number) count).longValue());
            }
        }
        long orderTotal = orderCounts.values().stream().mapToLong(Long::longValue).sum();

        LocalDateTime sevenDaysAgo = LocalDate.now().minusDays(NEW_USER_WINDOW_DAYS - 1L).atStartOfDay();
        LocalDateTime thirtyDaysAgo = LocalDate.now().minusDays(GMV_WINDOW_DAYS - 1L).atStartOfDay();

        BigDecimal creditAvg = statsMapper.averageCreditScore() == null
                ? ZERO_AMOUNT
                : statsMapper.averageCreditScore().setScale(2, RoundingMode.HALF_UP);

        return new StatsOverviewVO(
                userMapper.selectCount(null),
                userMapper.selectCount(Wrappers.<User>lambdaQuery().ge(User::getCreatedAt, sevenDaysAgo)),
                itemMapper.selectCount(null),
                itemMapper.selectCount(Wrappers.<Item>lambdaQuery().eq(Item::getStatus, ItemStatus.ON_SHELF)),
                itemMapper.selectCount(Wrappers.<Item>lambdaQuery().eq(Item::getStatus, ItemStatus.PENDING)),
                orderTotal,
                orderCounts.getOrDefault(OrderStatus.PENDING, 0L),
                orderCounts.getOrDefault(OrderStatus.CONFIRMED, 0L),
                orderCounts.getOrDefault(OrderStatus.IN_USE, 0L),
                orderCounts.getOrDefault(OrderStatus.OVERDUE, 0L),
                orderCounts.getOrDefault(OrderStatus.COMPLETED, 0L),
                orderCounts.getOrDefault(OrderStatus.CANCELLED, 0L),
                settlementMapper.selectCount(Wrappers.<Settlement>lambdaQuery()
                        .eq(Settlement::getStatus, SettlementStatus.PENDING_CONFIRM)),
                appealMapper.selectCount(Wrappers.<Appeal>lambdaQuery().eq(Appeal::getStatus, "PENDING")),
                amountOf(statsMapper.sumCompletedRent()),
                amountOf(statsMapper.sumCompletedRentSince(thirtyDaysAgo)),
                creditAvg,
                LocalDateTime.now());
    }

    @Override
    public StatsTrendVO trend(Integer days) {
        int span = normalizeDays(days);
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(span - 1L);
        LocalDateTime fromTime = from.atStartOfDay();

        Map<String, Map<String, Object>> orderByDate = indexByDate(statsMapper.orderTrend(fromTime));
        Map<String, Long> userByDate = new HashMap<>();
        for (Map<String, Object> row : statsMapper.userTrend(fromTime)) {
            userByDate.put(String.valueOf(row.get("d")), ((Number) row.get("c")).longValue());
        }

        // 空天补 0：后端补洞，前端拿到就是连续的 7 个点
        List<StatsTrendVO.PointVO> points = new ArrayList<>(span);
        for (int i = 0; i < span; i++) {
            String date = from.plusDays(i).toString();
            Map<String, Object> orderRow = orderByDate.get(date);
            long orderCount = orderRow == null ? 0L : ((Number) orderRow.getOrDefault("c", 0)).longValue();
            BigDecimal gmv = orderRow == null ? ZERO_AMOUNT : toAmount(orderRow.get("g"));
            points.add(new StatsTrendVO.PointVO(date, orderCount, gmv, userByDate.getOrDefault(date, 0L)));
        }
        return new StatsTrendVO(span, points);
    }

    private Map<String, Map<String, Object>> indexByDate(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            map.put(String.valueOf(row.get("d")), row);
        }
        return map;
    }

    private BigDecimal amountOf(BigDecimal value) {
        return value == null ? ZERO_AMOUNT : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal toAmount(Object value) {
        if (value == null) {
            return ZERO_AMOUNT;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private int normalizeDays(Integer days) {
        if (days == null) {
            return DEFAULT_TREND_DAYS;
        }
        if (days < MIN_TREND_DAYS || days > MAX_TREND_DAYS) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "days 应在 " + MIN_TREND_DAYS + "~" + MAX_TREND_DAYS + " 之间");
        }
        return days;
    }

    /** 预留：如果以后要看「进行中」合计，别再各写一遍。 */
    @SuppressWarnings("unused")
    private long activeOrders(StatsOverviewVO overview) {
        return overview.orderConfirmed() + overview.orderInUse() + overview.orderOverdue();
    }

    /** 预留：订单表里未使用，但保留常量引用以免 import 被判无用。 */
    @SuppressWarnings("unused")
    private long totalOrdersDirectly() {
        return rentalOrderMapper.selectCount(Wrappers.<RentalOrder>lambdaQuery());
    }
}
