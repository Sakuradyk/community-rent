package com.duyikang.rental.module.credit.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 一条信誉流水（K4）。`beforeScore / afterScore` 都落库，任何一次变动都能复盘。
 */
public record CreditLogVO(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long orderId,
        Integer change,
        Integer beforeScore,
        Integer afterScore,
        String reasonCode,
        String reasonDesc,
        LocalDateTime createdAt
) {
}
