package com.duyikang.rental.module.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 时段占用实体（K2 最后防线）。一笔跨 N 天的订单逐日展开成 N 行；uk_occupancy(item_id, occupy_date) 让并发插入必然冲突。⚠ 必须逐日展开，若改成一单一行，唯一索引永不触发。
 */
@Data
@TableName("order_occupancy")
public class OrderOccupancy {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 物品
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long itemId;

    /**
     * 订单
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /**
     * 被占用的那一天（单日粒度）
     */
    private LocalDate occupyDate;

    /**
     * 写入时间
     */
    private LocalDateTime createdAt;

}
