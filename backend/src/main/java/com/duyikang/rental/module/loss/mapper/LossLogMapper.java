package com.duyikang.rental.module.loss.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duyikang.rental.module.loss.entity.LossLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * LossLog 表 Mapper（对应 loss_log）。
 * 默认继承 BaseMapper 的 CRUD；需要复杂 SQL 时再加方法 + resources/mapper 下的 XML。
 */
@Mapper
public interface LossLogMapper extends BaseMapper<LossLog> {
}