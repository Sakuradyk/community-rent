package com.duyikang.rental.module.item.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;

/**
 * 列表卡片：只带列表页要显示的东西（封面、价格、押金、社区、热度、可租性）。
 *
 * <p>{@code available} 为 {@code Boolean} 且允许 null：只有前端问了日期才会算，
 * 没问就是 null（"没问"与"不可租"必须能区分，否则前端会误标红）。</p>
 */
public record ItemCardVO(

        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        String title,
        String coverUrl,
        BigDecimal dailyPrice,
        BigDecimal deposit,
        @JsonSerialize(using = ToStringSerializer.class)
        Long categoryId,
        String categoryName,
        @JsonSerialize(using = ToStringSerializer.class)
        Long communityId,
        String communityName,
        Integer rentCount,
        Boolean available
) {
}
