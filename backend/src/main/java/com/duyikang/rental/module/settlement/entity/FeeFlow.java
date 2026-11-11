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
 * 资金流水实体。把结算拆成可对账的条目；金额恒为正数，方向看 direction。毕设不接真实支付，这里记账面流水。
 */
@Data
@TableName("fee_flow")
public class FeeFlow {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 流水号，幂等键
     */
    private String flowNo;

    /**
     * 订单
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /**
     * 归属结算单
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long settlementId;

    /**
     * RENT 租金 / DEPOSIT 押金 / OVERDUE 逾期费 / LOSS 损耗费 / REFUND 退还
     */
    private String type;

    /**
     * IN 收 / OUT 出
     */
    private String direction;

    /**
     * 金额，恒为正数，方向看 direction
     */
    private BigDecimal amount;

    /**
     * 备注
     */
    private String remark;

    /**
     * 发生时间
     */
    private LocalDateTime createdAt;

}
