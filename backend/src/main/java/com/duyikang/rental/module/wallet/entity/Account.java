package com.duyikang.rental.module.wallet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账户（复式记账的账户主体）。
 *
 * <p>每个用户一行（懒创建）；全站另有唯一一行 {@code owner_type = EXTERNAL}，
 * 代表用户自己的银行卡/其它账户在账上的镜像，其余额为负数表示"已从外部净流入的资金"。</p>
 *
 * @author duyikang
 */
@Data
@TableName("account")
public class Account {

    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** USER 用户 / EXTERNAL 系统外部。 */
    private String ownerType;

    /** USER 时为 user.id；EXTERNAL 固定 0。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerId;

    /** 可用余额（EXTERNAL 允许为负）。 */
    private BigDecimal cashBalance;

    /** 冻结余额（在租订单的租金 + 押金）。 */
    private BigDecimal frozenBalance;

    /** 乐观锁版本号：所有余额变动都带 version 条件，避免读改写丢更新。 */
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updateTime;
}
