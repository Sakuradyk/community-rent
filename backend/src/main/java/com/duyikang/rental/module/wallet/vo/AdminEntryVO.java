package com.duyikang.rental.module.wallet.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理端全局分录行（跨账户的资金流向，用于对账与答辩演示）。
 *
 * @param id               分录 id
 * @param entryNo          分录号
 * @param bizType          业务类型
 * @param bizId            业务主键
 * @param action           动作
 * @param actionLabel      动作中文
 * @param fromOwnerType    出账方类型
 * @param fromOwnerId      出账方 id
 * @param fromName         出账方名称
 * @param toOwnerType      入账方类型
 * @param toOwnerId        入账方 id
 * @param toName           入账方名称
 * @param fromBucket       出账桶
 * @param toBucket         入账桶
 * @param amount           金额
 * @param fromBalanceAfter 出账后余额
 * @param toBalanceAfter   入账后余额
 * @param remark           说明
 * @param createdAt        记账时间
 * @author duyikang
 */
public record AdminEntryVO(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String entryNo,
        String bizType,
        @JsonSerialize(using = ToStringSerializer.class) Long bizId,
        String action,
        String actionLabel,
        String fromOwnerType,
        @JsonSerialize(using = ToStringSerializer.class) Long fromOwnerId,
        String fromName,
        String toOwnerType,
        @JsonSerialize(using = ToStringSerializer.class) Long toOwnerId,
        String toName,
        String fromBucket,
        String toBucket,
        BigDecimal amount,
        BigDecimal fromBalanceAfter,
        BigDecimal toBalanceAfter,
        String remark,
        LocalDateTime createdAt) {
}
