package com.duyikang.rental.module.wallet.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对账结果（管理端「账务自检」）。
 *
 * <p>三个数字足以证明账没记错：①所有账户余额之和必须为 0（复式记账的守恒）；
 * ②外部账户为负且绝对值等于用户侧总额；③分录条数用来发现"记了钱却没写分录"这类半成品。</p>
 *
 * @param externalCash  外部账户余额（负数，代表已从外部净流入的资金）
 * @param userCash      全体用户可用余额之和
 * @param userFrozen    全体用户冻结余额之和
 * @param balanceSum    三者之和，正常恒为 0
 * @param balanced      是否守恒
 * @param entryCount    分录总条数
 * @param accountCount  账户总行数
 * @param checkedAt     检查时间
 * @param issues        发现的问题描述（为空表示干净）
 * @author duyikang
 */
public record ReconcileVO(
        BigDecimal externalCash,
        BigDecimal userCash,
        BigDecimal userFrozen,
        BigDecimal balanceSum,
        boolean balanced,
        long entryCount,
        long accountCount,
        LocalDateTime checkedAt,
        List<String> issues) {
}
