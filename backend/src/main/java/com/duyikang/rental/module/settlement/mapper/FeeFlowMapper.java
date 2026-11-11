package com.duyikang.rental.module.settlement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duyikang.rental.module.settlement.entity.FeeFlow;
import org.apache.ibatis.annotations.Mapper;

/**
 * FeeFlow 表 Mapper（对应 fee_flow）。
 * 默认继承 BaseMapper 的 CRUD；需要复杂 SQL 时再加方法 + resources/mapper 下的 XML。
 */
@Mapper
public interface FeeFlowMapper extends BaseMapper<FeeFlow> {
}