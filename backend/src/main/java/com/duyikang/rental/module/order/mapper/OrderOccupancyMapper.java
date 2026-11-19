package com.duyikang.rental.module.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duyikang.rental.module.order.entity.OrderOccupancy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * OrderOccupancy 表 Mapper（对应 order_occupancy）。
 *
 * <p>逐日展开后，"某个物品在某段日期里哪些天被占"是最热的查询（试算、下单预检、日历、建议时段），
 * 统一由 {@link #selectOccupiedDates} 用一条 SQL 回答，避免在 Java 里拼日期。</p>
 */
@Mapper
public interface OrderOccupancyMapper extends BaseMapper<OrderOccupancy> {

    /**
     * 查某物品在 {@code [from, to)} 内被占用的日期（升序、去重）。
     *
     * <p>"占用"的口径：订单状态 ∈ PENDING / CONFIRMED / IN_USE / OVERDUE。
     * <b>PENDING 也要算</b>——下单即占时段，否则待确认期间同一物品会被重复下单，
     * K2 的"同一时段零放行"当场失效。</p>
     *
     * @param itemId 物品 id
     * @param from   起始日（含）
     * @param to     截止日（不含）
     */
    List<LocalDate> selectOccupiedDates(@Param("itemId") Long itemId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);
}
