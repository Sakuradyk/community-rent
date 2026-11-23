package com.duyikang.rental.module.recommend.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;

/**
 * 推荐列表的一项。
 *
 * <p>字段口径见 {@code docs/接口文档.md} §6.1：`score` 是混合打分结果（保留 4 位小数），
 * `reasonCode` 是四选一的枚举，`reason` 是**后端拼好的整句**（前端只渲染，不许自己按 code 再翻译一遍，
 * 否则两端文案迟早不一致）。</p>
 *
 * @param id            物品 id（字符串化，前端不许 Number()）
 * @param title         标题
 * @param coverUrl      封面相对路径
 * @param categoryId    二级类目 id
 * @param categoryName  二级类目名
 * @param communityId   所属小区 id
 * @param communityName 所属小区名
 * @param dailyPrice    日租金（仅展示，算钱一律走后端 quote）
 * @param deposit       押金
 * @param rentCount     累计租出次数（热度的一部分）
 * @param score         混合得分，0–1
 * @param reasonCode    CF / CONTENT / HEAT / COLD_START
 * @param reason        可直接展示的一句推荐理由
 */
public record RecommendItemVO(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String title,
        String coverUrl,
        @JsonSerialize(using = ToStringSerializer.class) Long categoryId,
        String categoryName,
        @JsonSerialize(using = ToStringSerializer.class) Long communityId,
        String communityName,
        BigDecimal dailyPrice,
        BigDecimal deposit,
        Integer rentCount,
        BigDecimal score,
        String reasonCode,
        String reason) {
}
