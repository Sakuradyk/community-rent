package com.duyikang.rental.module.item.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 社区下拉选项。
 *
 * <p>只暴露前端画下拉需要的三个字段，不返回 {@code Community} 实体
 * （实体含 alias / enabled 等管理端字段，PROJECT_RULES.md §4.3「Entity 不直接返回前端」）。</p>
 *
 * @param id       社区主键（序列化为字符串，避免前端 Number 精度丢失，§4.6 F9）
 * @param name     小区名
 * @param district 所属片区，前端可用于分组显示
 */
public record CommunityVO(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String name,
        String district
) {
}
