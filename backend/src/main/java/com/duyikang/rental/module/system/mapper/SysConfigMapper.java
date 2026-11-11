package com.duyikang.rental.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duyikang.rental.module.system.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * SysConfig 表 Mapper（对应 sys_config）。
 * 默认继承 BaseMapper 的 CRUD；需要复杂 SQL 时再加方法 + resources/mapper 下的 XML。
 */
@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {
}