package com.duyikang.rental.module.loss.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duyikang.rental.module.loss.entity.LossPolicy;
import org.apache.ibatis.annotations.Mapper;

/**
 * LossPolicy 表 Mapper（对应 loss_policy）。
 * 默认继承 BaseMapper 的 CRUD；需要复杂 SQL 时再加方法 + resources/mapper 下的 XML。
 */
@Mapper
public interface LossPolicyMapper extends BaseMapper<LossPolicy> {
}