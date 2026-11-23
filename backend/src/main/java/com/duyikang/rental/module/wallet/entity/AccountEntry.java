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
 * 分录（复式记账，只追加、不改写）。
 *
 * <p>一行就是一次资金移动：{@code from} 桶减少 {@code amount}，{@code to} 桶增加 {@code amount}，
 * 因此「全站账户余额之和恒为 0」可以随时用 SQL 验证。
 * {@code (biz_type, biz_id, action, from_account_id, to_account_id)} 唯一，
 * 保证同一业务的同一动作只入账一次（双击、重试、定时任务重跑都安全）。</p>
 *
 * @author duyikang
 */
@Data
@TableName("account_entry")
public class AccountEntry {

    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 分录号：F + yyyyMMdd + 4 位当日序号 + 2 位组内序号。 */
    private String entryNo;

    /** RECHARGE / ORDER_FREEZE / ORDER_RELEASE / SETTLE / VERDICT / ADMIN_ADJUST。 */
    private String bizType;

    /** 关联业务主键：订单 id / 结算单 id / 0 表示无单据。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bizId;

    /** FREEZE / RELEASE / RENT / OVERDUE / LOSS / REFUND / ADJUST / RECHARGE。 */
    private String action;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromAccountId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long toAccountId;

    /** 出账桶：CASH / FROZEN。 */
    private String fromBucket;

    /** 入账桶：CASH / FROZEN。 */
    private String toBucket;

    private BigDecimal amount;

    /** 出账后该账户该桶的余额快照（事后对账用，不靠重算猜）。 */
    private BigDecimal fromBalanceAfter;

    /** 入账后该账户该桶的余额快照。 */
    private BigDecimal toBalanceAfter;

    private String remark;

    private LocalDateTime createdAt;
}
