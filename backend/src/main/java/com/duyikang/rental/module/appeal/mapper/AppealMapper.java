package com.duyikang.rental.module.appeal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duyikang.rental.module.appeal.entity.Appeal;
import org.apache.ibatis.annotations.Mapper;

/**
 * Appeal 表 Mapper（对应 appeal）。
 * 默认继承 BaseMapper 的 CRUD；需要复杂 SQL 时再加方法 + resources/mapper 下的 XML。
 */
@Mapper
public interface AppealMapper extends BaseMapper<Appeal> {
}