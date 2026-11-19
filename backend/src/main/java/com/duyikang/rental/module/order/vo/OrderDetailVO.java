package com.duyikang.rental.module.order.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单详情（{@code GET /api/orders/{id}}）。
 *
 * <p>切片 3 只读；切片 4 会追加 {@code confirmedAt / inUseAt / completedAt / cancelledAt /
 * cancelReason} 与各种动作，**只加字段不改已有字段**，所以前端现在就可以按这个结构写页面。</p>
 */
public record OrderDetailVO(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String orderNo,
        String status,
        String statusLabel,
        @JsonSerialize(using = ToStringSerializer.class) Long itemId,
        String itemTitle,
        String itemCoverUrl,
        String categoryName,
        String communityName,
        LocalDate startDate,
        LocalDate endDate,
        Integer rentDays,
        BigDecimal dailyPrice,
        BigDecimal depositAmount,
        BigDecimal overdueRate,
        BigDecimal rentAmount,
        BigDecimal totalAmount,
        String remark,
        Party owner,
        Party renter,
        LocalDateTime createdAt
) {

    /**
     * 订单双边的展示信息。只给昵称：详情页不需要手机号，少给一个字段就少一处泄露面。
     */
    public record Party(
            @JsonSerialize(using = ToStringSerializer.class) Long id,
            String nickname
    ) {
    }
}
