package com.duyikang.rental.module.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duyikang.rental.module.credit.entity.CreditPolicy;
import org.apache.ibatis.annotations.Mapper;

/**
 * CreditPolicy 表 Mapper（对应 credit_policy）。
 * 默认继承 BaseMapper 的 CRUD；需要复杂 SQL 时再加方法 + resources/mapper 下的 XML。
 */
@Mapper
public interface CreditPolicyMapper extends BaseMapper<CreditPolicy> {
}