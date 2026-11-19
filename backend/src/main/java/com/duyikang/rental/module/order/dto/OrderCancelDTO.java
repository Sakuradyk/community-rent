package com.duyikang.rental.module.order.dto;

import jakarta.validation.constraints.Size;

/**
 * 取消订单入参（切片 4）。
 *
 * <p>只允许填理由，不允许指定"取消后变成什么"—— 目标状态由服务端状态机决定。</p>
 */
public record OrderCancelDTO(
        @Size(max = 255) String reason
) {
}
