package com.duyikang.rental.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duyikang.rental.module.item.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * Category 表 Mapper（对应 category）。
 * 默认继承 BaseMapper 的 CRUD；需要复杂 SQL 时再加方法 + resources/mapper 下的 XML。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}