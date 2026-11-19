package com.duyikang.rental.module.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 创建订单入参（{@code POST /api/orders}）。
 *
 * <p>没有任何金额字段——这是刻意的：金额由服务端按物品当前价格重算并快照。
 * 如果允许前端传金额，用户改一个数字就能一块钱租走相机。</p>
 *
 * @param itemId    物品 id
 * @param startDate 起租日（含）
 * @param endDate   归还日（不含）
 * @param remark    备注，可空
 */
public record OrderCreateDTO(
        @NotNull(message = "物品不能为空") Long itemId,
        @NotNull(message = "起租日不能为空") LocalDate startDate,
        @NotNull(message = "归还日不能为空") LocalDate endDate,
        @Size(max = 255, message = "备注不能超过 255 字") String remark
) {
}
