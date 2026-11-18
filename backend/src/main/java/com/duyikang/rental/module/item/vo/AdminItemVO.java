package com.duyikang.rental.module.item.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端待审/全部物品列表项。
 *
 * <p>比用户端多三样：{@code ownerNickname}（审核要认人）、{@code ownerPhone}（脱敏，出问题要能追人）、
 * {@code images}（不看到图没法审）。手机号脱敏规则复用 {@code UserConvert.maskPhone}。</p>
 */
public record AdminItemVO(

        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        String title,
        String description,
        String coverUrl,
        BigDecimal dailyPrice,
        BigDecimal deposit,
        BigDecimal overdueRate,
        @JsonSerialize(using = ToStringSerializer.class)
        Long categoryId,
        String categoryName,
        @JsonSerialize(using = ToStringSerializer.class)
        Long communityId,
        String communityName,
        String status,
        String statusLabel,
        String auditRemark,
        @JsonSerialize(using = ToStringSerializer.class)
        Long ownerId,
        String ownerNickname,
        String ownerPhone,
        List<ItemImageVO> images,
        LocalDateTime createdAt
) {
}
