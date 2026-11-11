package com.duyikang.rental.module.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duyikang.rental.module.order.entity.RentalOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * RentalOrder 表 Mapper（对应 rental_order）。
 * 默认继承 BaseMapper 的 CRUD；需要复杂 SQL 时再加方法 + resources/mapper 下的 XML。
 */
@Mapper
public interface RentalOrderMapper extends BaseMapper<RentalOrder> {
}