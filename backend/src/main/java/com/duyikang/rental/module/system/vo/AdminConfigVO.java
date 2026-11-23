package com.duyikang.rental.module.system.vo;

import com.duyikang.rental.module.credit.entity.CreditPolicy;
import com.duyikang.rental.module.loss.entity.LossPolicy;
import com.duyikang.rental.module.settlement.entity.FeePolicy;

import java.util.List;

/**
 * 管理端参数配置页的一次性读取结果（{@code GET /api/admin/config}）。
 *
 * <p>三张策略表是"单行结构化表"（id 固定 1，列名即参数名），所以直接回实体，
 * 不做 DTO 映射 —— 前端要的就是「字段名 + 当前值」，多包一层只会让两边各维护一份字段清单。
 * {@code sys_config} 是键值表，回精简后的 {@code SysItem}（不暴露 id/描述里的内部细节）。</p>
 */
public record AdminConfigVO(
        CreditPolicy creditPolicy,
        FeePolicy feePolicy,
        LossPolicy lossPolicy,
        List<SysItem> sysConfig) {

    /**
     * @param key         参数键（如 {@code recommend.w.cf}）
     * @param value       当前值（字符串，前端按 valueType 渲染输入控件）
     * @param valueType   {@code INT / DECIMAL / BOOLEAN / CRON}
     * @param group       归属分组（recommend / order / community / settlement / file …）
     * @param description 中文说明
     */
    public record SysItem(String key, String value, String valueType, String group, String description) {
    }
}
