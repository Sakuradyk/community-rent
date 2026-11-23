package com.duyikang.rental.module.wallet.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理端账户行（对账用）。
 *
 * @param accountId     账户 id
 * @param ownerType     USER / EXTERNAL
 * @param ownerId       所有者 id
 * @param nickname      昵称（EXTERNAL 为「系统外部」）
 * @param cashBalance   可用余额
 * @param frozenBalance 冻结余额
 * @param totalBalance  合计
 * @param entryCount    分录条数（能看出这个账户有没有记过账）
 * @param updateTime    最后变动时间
 * @author duyikang
 */
public record AdminAccountVO(
        @JsonSerialize(using = ToStringSerializer.class) Long accountId,
        String ownerType,
        @JsonSerialize(using = ToStringSerializer.class) Long ownerId,
        String nickname,
        BigDecimal cashBalance,
        BigDecimal frozenBalance,
        BigDecimal totalBalance,
        long entryCount,
        LocalDateTime updateTime) {
}
