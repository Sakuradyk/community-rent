package com.duyikang.rental.module.loss.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 损耗记录实体（K6）。每次归还记一次 w = 租期系数 × 类目系数 × 归还状态系数，并存累计 L 的前后值。
 */
@Data
@TableName("loss_log")
public class LossLog {

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
     * 来源订单
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /**
     * 租期系数（按天数梯度）
     */
    private BigDecimal termCoef;

    /**
     * 类目系数（来自 category.loss_coef）
     */
    private BigDecimal categoryCoef;

    /**
     * 归还状态系数：完好 1.0 / 轻微磨损 1.5 / 损坏 3.0
     */
    private BigDecimal returnCoef;

    /**
     * 本次损耗权重 w = 三者乘积
     */
    private BigDecimal weight;

    /**
     * 累计 L（变动前）
     */
    private BigDecimal lossBefore;

    /**
     * 累计 L（变动后，截断到 [0,1]）
     */
    private BigDecimal lossAfter;

    /**
     * INTACT 完好 / WORN 轻微磨损 / DAMAGED 损坏
     */
    private String returnState;

    /**
     * NONE / MAINTAIN 保养提醒 / REPAIR 强制检修
     */
    private String action;

    /**
     * 记录时间
     */
    private LocalDateTime createdAt;

}
