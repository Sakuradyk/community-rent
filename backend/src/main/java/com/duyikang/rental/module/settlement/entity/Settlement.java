package com.duyikang.rental.module.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 结算单实体（K3）。uk_settle_order 保证一单一结算，uk_settle_no 保证单号幂等。应退押金 = max(押金 − 逾期费 − 损耗费, 0)。lossRatio 存本次 w，不是累计 L。
 */
@Data
@TableName("settlement")
public class Settlement {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 订单（一单一结算）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /**
     * 结算单号，幂等键
     */
    private String settleNo;

    /**
     * 租金
     */
    private BigDecimal rentAmount;

    /**
     * 押金（= 订单快照 deposit）
     */
    private BigDecimal depositAmount;

    /**
     * 逾期天数
     */
    private Integer overdueDays;

    /**
     * 逾期费 = Σ 日租金 × 阶梯费率
     */
    private BigDecimal overdueFee;

    /**
     * 本次归还带来的损耗值（本次 w，非累计 L）
     */
    private BigDecimal lossRatio;

    /**
     * 损耗费
     */
    private BigDecimal lossFee;

    /**
     * 应退押金 = max(押金 − 逾期费 − 损耗费, 0)
     */
    private BigDecimal refundDeposit;

    /**
     * PENDING_CONFIRM 待承租方确认 / SETTLED 已结算 / DISPUTED 有异议（申诉中）/ ADJUSTED 已被裁决调整
     */
    private String status;

    /**
     * 冷静期截止 = 归还时间 + settlement.confirm.hours（PENDING_CONFIRM 必填）
     */
    private LocalDateTime confirmDeadline;

    /**
     * 承租方点「无异议」的时间
     */
    private LocalDateTime confirmedAt;

    /**
     * 承租方提出异议的时间
     */
    private LocalDateTime disputedAt;

    /**
     * 操作人（系统自动结算时为 null）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    /**
     * 备注
     */
    private String remark;

    /**
     * 结算生效时间（SETTLED / ADJUSTED 时写入）
     */
    private LocalDateTime settledAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

}
