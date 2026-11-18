package com.duyikang.rental.module.item.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 物品详情。
 *
 * <p>{@code maintenanceState} 是"由 loss_value 与 loss_policy 阈值算出来的展示值"（NORMAL/MAINTAIN/REPAIR），
 * 与 {@code status} 不是一回事：status 是业务状态机，maintenanceState 是损耗程度。
 * 前端不该自己拿 lossValue 和阈值比较——阈值在配置表里，前端拿不到也不该拿（F1）。</p>
 */
public record ItemDetailVO(

        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        String title,
        String description,
        @JsonSerialize(using = ToStringSerializer.class)
        Long categoryId,
        String categoryName,
        @JsonSerialize(using = ToStringSerializer.class)
        Long communityId,
        String communityName,
        BigDecimal dailyPrice,
        BigDecimal deposit,
        BigDecimal overdueRate,
        BigDecimal lossValue,
        String maintenanceState,
        String status,
        List<ItemImageVO> images,
        ItemOwnerVO owner,
        Integer rentCount,
        Integer viewCount,
        LocalDateTime createdAt
) {
}
