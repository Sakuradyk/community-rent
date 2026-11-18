package com.duyikang.rental.module.item.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 类目字典项（发布页/筛选下拉用）。
 *
 * <p>为什么返回扁平结构而不是树：类目最多两级，前端用 {@code parentId} 自己组装两级下拉，
 * 比后端拼嵌套对象更简单，也避免"改了前两级就得改后端 VO"的耦合。</p>
 *
 * @param id       类目主键（字符串下发，遵守 docs/接口文档.md 的 id 约定）
 * @param name     类目名
 * @param parentId 父类目主键；0 表示一级类目
 * @param level    层级：1 一级 / 2 二级（发布物品只能选二级）
 */
public record CategoryVO(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String name,
        @JsonSerialize(using = ToStringSerializer.class) Long parentId,
        Integer level) {
}
