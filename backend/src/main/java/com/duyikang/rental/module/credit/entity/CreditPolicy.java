package com.duyikang.rental.module.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 信誉策略实体（单行表，id 固定 1）。加分日封顶、逾期阶梯扣分、押金档位都在这里；禁止在代码里写死分值。
 */
@Data
@TableName("credit_policy")
public class CreditPolicy {

    /**
     * 固定为 1，单行表
     */
    @TableId(type = IdType.INPUT)
    private Integer id;

    /**
     * 初始分
     */
    private Integer initScore;

    /**
     * 下界
     */
    private Integer minScore;

    /**
     * 上界
     */
    private Integer maxScore;

    /**
     * 按时归还加分
     */
    private Integer rewardOnTime;

    /**
     * 日加分上限
     */
    private Integer rewardDailyCap;

    /**
     * 轻微逾期（≤3 天）每天扣分，负值
     */
    private Integer penaltyOverdueLight;

    /**
     * 严重逾期（>3 天）每天扣分，负值
     */
    private Integer penaltyOverdueHeavy;

    /**
     * 损坏扣分
     */
    private Integer penaltyDamage;

    /**
     * 确认后取消扣分
     */
    private Integer penaltyCancelAfterConfirm;

    /**
     * 免押金门槛（≥）
     */
    private Integer tierFreeDepositMin;

    /**
     * 正常租用区间下界
     */
    private Integer tierNormalMin;

    /**
     * 正常租用区间上界
     */
    private Integer tierNormalMax;

    /**
     * 押金上浮区间下界
     */
    private Integer tierDepositUpMin;

    /**
     * 押金上浮区间上界
     */
    private Integer tierDepositUpMax;

    /**
     * 押金上浮系数
     */
    private BigDecimal tierDepositUpRatio;

    /**
     * 低于此值暂停租用资格
     */
    private Integer tierSuspendBelow;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
