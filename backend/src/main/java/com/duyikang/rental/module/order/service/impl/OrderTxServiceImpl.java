package com.duyikang.rental.module.order.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.duyikang.rental.common.constant.OrderStatus;
import com.duyikang.rental.module.item.entity.Item;
import com.duyikang.rental.module.order.entity.OrderOccupancy;
import com.duyikang.rental.module.order.entity.RentalOrder;
import com.duyikang.rental.module.order.mapper.OrderOccupancyMapper;
import com.duyikang.rental.module.order.mapper.RentalOrderMapper;
import com.duyikang.rental.module.order.service.OrderTxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 下单事务实现：{@code INSERT rental_order} + 按 {@code [start, end)} **逐日** {@code INSERT order_occupancy}。
 *
 * <p><b>逐日展开是 K2 唯一索引生效的前提</b>：如果一笔跨 3 天的订单只写 1 行（存 start/end），
 * 那么任意两笔订单的 {@code (item_id, occupy_date)} 组合天然不同，
 * {@code uk_occupancy(item_id, occupy_date)} 永远不会触发，第三层防线等于不存在。
 * 展开后 11-20 → 11-23 写 3 行（20/21/22），与计费天数、日历高亮完全一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTxServiceImpl implements OrderTxService {

    private static final DateTimeFormatter ORDER_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RentalOrderMapper rentalOrderMapper;
    private final OrderOccupancyMapper orderOccupancyMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInTx(Item item, Long renterId, LocalDate startDate, LocalDate endDate,
                           int rentDays, String remark) {
        BigDecimal rentAmount = item.getDailyPrice()
                .multiply(BigDecimal.valueOf(rentDays))
                .setScale(2, RoundingMode.HALF_UP);

        RentalOrder order = new RentalOrder();
        order.setOrderNo(nextOrderNo());
        order.setItemId(item.getId());
        order.setOwnerId(item.getOwnerId());
        order.setRenterId(renterId);
        order.setStartDate(startDate);
        order.setEndDate(endDate);
        order.setRentDays(rentDays);
        order.setStatus(OrderStatus.PENDING);
        // 三列价格快照：之后出租方改价，这单的金额不变
        order.setDailyPrice(item.getDailyPrice());
        order.setDeposit(item.getDeposit());
        order.setOverdueRate(item.getOverdueRate());
        order.setRentAmount(rentAmount);
        order.setRemark(remark);
        rentalOrderMapper.insert(order);

        for (LocalDate day = startDate; day.isBefore(endDate); day = day.plusDays(1)) {
            OrderOccupancy occupancy = new OrderOccupancy();
            occupancy.setItemId(item.getId());
            occupancy.setOrderId(order.getId());
            occupancy.setOccupyDate(day);
            orderOccupancyMapper.insert(occupancy);
        }

        log.info("订单创建成功 orderId={} orderNo={} itemId={} renterId={} {}~{} 共 {} 天",
                order.getId(), order.getOrderNo(), item.getId(), renterId, startDate, endDate, rentDays);
        return order.getId();
    }

    /**
     * 生成业务单号：{@code R + yyyyMMdd + 4 位当日序号}（如 {@code R202611190001}）。
     *
     * <p>序号取"当日已有订单数 + 1"，并发下可能撞 {@code uk_order_no}——那种情况由调用方
     * 判定异常信息后重试，属于极端情况（同一毫秒内两单）。这里不引入序列表，
     * 毕设规模下"撞了就重试一次"比多一张表更划算。</p>
     */
    private String nextOrderNo() {
        String prefix = "R" + LocalDate.now().format(ORDER_NO_DATE);
        Long todayCount = rentalOrderMapper.selectCount(
                Wrappers.<RentalOrder>lambdaQuery().likeRight(RentalOrder::getOrderNo, prefix));
        return prefix + String.format("%04d", todayCount + 1);
    }
}
