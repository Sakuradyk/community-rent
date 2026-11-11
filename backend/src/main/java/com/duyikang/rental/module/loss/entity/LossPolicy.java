package com.duyikang.rental.module.loss.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 损耗策略实体（单行表，id 固定 1）。0.6 保养 / 0.85 强制检修两个阈值与三档租期系数都在这里。
 */
@Data
@TableName("loss_policy")
public class LossPolicy {

    /**
     * 固定为 1，单行表
     */
    @TableId(type = IdType.INPUT)
    private Integer id;

    /**
     * 保养提醒阈值
     */
    private BigDecimal thresholdMaintain;

    /**
     * 强制检修阈值
     */
    private BigDecimal thresholdRepair;

    /**
     * 租期梯度一：1–3 天
     */
    private Integer termTier1MaxDays;

    /**
     * 租期梯度一系数
     */
    private BigDecimal termTier1Coef;

    /**
     * 租期梯度二：4–7 天
     */
    private Integer termTier2MaxDays;

    /**
     * 租期梯度二系数
     */
    private BigDecimal termTier2Coef;

    /**
     * 租期梯度三：>7 天
     */
    private BigDecimal termTier3Coef;

    /**
     * 完好
     */
    private BigDecimal returnCoefIntact;

    /**
     * 轻微磨损
     */
    private BigDecimal returnCoefWorn;

    /**
     * 损坏
     */
    private BigDecimal returnCoefDamaged;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
