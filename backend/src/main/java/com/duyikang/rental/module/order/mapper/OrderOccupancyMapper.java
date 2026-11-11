package com.duyikang.rental.module.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duyikang.rental.module.order.entity.OrderOccupancy;
import org.apache.ibatis.annotations.Mapper;

/**
 * OrderOccupancy 表 Mapper（对应 order_occupancy）。
 * 默认继承 BaseMapper 的 CRUD；需要复杂 SQL 时再加方法 + resources/mapper 下的 XML。
 */
@Mapper
public interface OrderOccupancyMapper extends BaseMapper<OrderOccupancy> {
}