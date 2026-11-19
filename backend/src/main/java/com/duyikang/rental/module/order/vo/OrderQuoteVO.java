package com.duyikang.rental.module.order.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 费用试算结果（{@code POST /api/orders/quote}）。
 *
 * <p>字段口径见 {@code docs/接口文档.md} §3.1。金额都是 JSON number，
 * 全部来自 {@code item} 表的价格与策略表算出的天数，前端只负责展示。</p>
 */
public record OrderQuoteVO(
        @JsonSerialize(using = ToStringSerializer.class) Long itemId,
        LocalDate startDate,
        LocalDate endDate,
        Integer rentDays,
        BigDecimal dailyPrice,
        /** 押金，与 {@link #depositAmount} 同值（一个是订单字段名，一个是页面展示名） */
        BigDecimal deposit,
        BigDecimal overdueRate,
        BigDecimal rentAmount,
        BigDecimal depositAmount,
        BigDecimal totalAmount
) {
}
