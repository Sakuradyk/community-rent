package com.duyikang.rental.module.wallet.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;

/**
 * 钱包快照（「我的」页面顶部的三行数字）。
 *
 * <p>为什么返回三行而不是一个数字：用户需要分清「能花的钱」和「被订单锁住的钱」，
 * 只看可用余额会以为钱少了，只看总额会以为还能继续下单。</p>
 *
 * @param accountId     账户 id
 * @param cashBalance   可用余额
 * @param frozenBalance 冻结中（在租订单的租金 + 押金）
 * @param totalBalance  账户总额 = 可用 + 冻结
 * @author duyikang
 */
public record WalletVO(
        @JsonSerialize(using = ToStringSerializer.class) Long accountId,
        BigDecimal cashBalance,
        BigDecimal frozenBalance,
        BigDecimal totalBalance) {
}
