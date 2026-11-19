package com.duyikang.rental.module.order.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 创建订单的返回（{@code POST /api/orders}）。
 *
 * <p>只回最少够用的字段：前端拿到 id 就能跳详情页，拿到 orderNo 能展示给用户看，
 * 金额回显让下单页和试算页对得上（服务端重算过的值，可能与试算时的价格不同——
 * 出租方在两次调用之间改了价）。</p>
 */
public record OrderCreatedVO(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String orderNo,
        String status,
        String statusLabel,
        LocalDate startDate,
        LocalDate endDate,
        Integer rentDays,
        BigDecimal rentAmount,
        BigDecimal depositAmount,
        BigDecimal totalAmount
) {
}
