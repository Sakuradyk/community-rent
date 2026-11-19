package com.duyikang.rental.module.order.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单列表行（{@code GET /api/orders}）。
 *
 * <p>列表只给"能一眼认出是哪一单"的字段：物品缩略信息、租期、金额、状态、对方昵称。
 * 想看费用拆项与时间戳去详情页（{@code GET /api/orders/{id}}），避免列表页 N 次查库。</p>
 *
 * @param role 当前用户在这笔订单里的身份：{@code renter}（我租的）或 {@code owner}（我出租的）
 * @param counterpartyNickname 对方的昵称（承租人视角看到出租方，反之亦然），不含手机号
 */
public record OrderListItemVO(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String orderNo,
        @JsonSerialize(using = ToStringSerializer.class) Long itemId,
        String itemTitle,
        String itemCoverUrl,
        LocalDate startDate,
        LocalDate endDate,
        Integer rentDays,
        BigDecimal rentAmount,
        BigDecimal depositAmount,
        BigDecimal totalAmount,
        String status,
        String statusLabel,
        String role,
        String counterpartyNickname,
        LocalDateTime createdAt
) {
}
