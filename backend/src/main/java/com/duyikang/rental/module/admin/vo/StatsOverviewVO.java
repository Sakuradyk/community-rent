package com.duyikang.rental.module.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理端概览（{@code GET /api/admin/stats/overview}）。
 *
 * <p>口径见 {@code docs/接口文档.md} §6.2：GMV 只算租金（{@code rent_amount}）、只算已完成的订单；
 * 「进行中」= `CONFIRMED + IN_USE + OVERDUE`；`creditAvg` 只统计启用用户。</p>
 */
public record StatsOverviewVO(
        long userTotal,
        long userNewIn7Days,
        long itemTotal,
        long itemOnShelf,
        long itemPendingAudit,
        long orderTotal,
        long orderPending,
        long orderConfirmed,
        long orderInUse,
        long orderOverdue,
        long orderCompleted,
        long orderCancelled,
        long settlementPendingConfirm,
        long appealPending,
        BigDecimal gmvTotal,
        BigDecimal gmvIn30Days,
        BigDecimal creditAvg,
        LocalDateTime updatedAt) {
}
