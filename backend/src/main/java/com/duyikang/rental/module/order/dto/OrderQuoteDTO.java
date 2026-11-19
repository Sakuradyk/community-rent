package com.duyikang.rental.module.order.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 费用试算入参（{@code POST /api/orders/quote}）。
 *
 * <p>只带物品与区间三个字段：算钱需要的信息全部来自库里的物品价格快照来源（item 表）与策略表，
 * 前端不传金额——传了也不看，避免"前端算一套、后端算一套"。</p>
 *
 * @param itemId    物品 id
 * @param startDate 起租日（含）
 * @param endDate   归还日（不含）
 */
public record OrderQuoteDTO(
        @NotNull(message = "物品不能为空") Long itemId,
        @NotNull(message = "起租日不能为空") LocalDate startDate,
        @NotNull(message = "归还日不能为空") LocalDate endDate
) {
}
