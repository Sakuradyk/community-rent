package com.duyikang.rental.module.item.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 「我的物品」列表项：卡片字段 + 全部状态（不只 ON_SHELF）+ 审核意见。
 *
 * <p>为什么带 {@code statusLabel}：状态中文名由后端 {@code ItemStatus.labelOf} 给，
 * 前端只负责按 status 选颜色——避免前后端各维护一份中文名。</p>
 */
public record MyItemVO(

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
        String status,
        String statusLabel,
        String auditRemark,
        Integer imageCount,
        /**
         * 累计损耗值（0–1）。放在「我的物品」列表里，是为了让物主一眼看出哪件该保养了 ——
         * K6 的「损耗可见」不只在详情页体现。
         */
        BigDecimal lossValue,
        LocalDateTime createdAt
) {
}
