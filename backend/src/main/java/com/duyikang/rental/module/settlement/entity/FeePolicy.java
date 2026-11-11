package com.duyikang.rental.module.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 费用策略实体（单行表，id 固定 1）。所有费率与上下界都从这里读，禁止写死在代码里。id 非自增，故 IdType.INPUT。
 */
@Data
@TableName("fee_policy")
public class FeePolicy {

    /**
     * 固定为 1，单行表
     */
    @TableId(type = IdType.INPUT)
    private Integer id;

    /**
     * 阶梯一适用天数上限
     */
    private Integer overdueTier1Days;

    /**
     * 阶梯一费率（×日租金/天）
     */
    private BigDecimal overdueTier1Rate;

    /**
     * 阶梯二费率
     */
    private BigDecimal overdueTier2Rate;

    /**
     * 物品逾期费率下界
     */
    private BigDecimal overdueRateMin;

    /**
     * 物品逾期费率上界
     */
    private BigDecimal overdueRateMax;

    /**
     * 日租金下界
     */
    private BigDecimal dailyPriceMin;

    /**
     * 日租金上界
     */
    private BigDecimal dailyPriceMax;

    /**
     * 押金/日租金 下界
     */
    private BigDecimal depositRatioMin;

    /**
     * 押金/日租金 上界
     */
    private BigDecimal depositRatioMax;

    /**
     * 确认后取消免罚的提前小时数
     */
    private Integer cancelFreeHours;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
