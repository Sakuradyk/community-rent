package com.duyikang.rental.module.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duyikang.rental.module.wallet.entity.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 账户 Mapper。
 *
 * <p>余额变动全部走这里的条件更新（{@code WHERE ... AND 余额 >= amount}），
 * 不做「先查再改」——那是丢更新的根源。受影响行数为 0 即代表余额不足或账户被并发改过，
 * 由 Service 层翻译成 4005 或重试。</p>
 *
 * @author duyikang
 */
@Mapper
public interface AccountMapper extends BaseMapper<Account> {

    /**
     * 取外部账户 id（全站唯一一行，种子数据已有；缺失时由 Service 兜底创建）。
     */
    @Select("SELECT id FROM `account` WHERE owner_type = 'EXTERNAL' AND owner_id = 0")
    Long selectExternalId();

    /**
     * 可用余额 → 冻结（下单冻结：同一账户的两个桶之间移动）。
     */
    @Update("UPDATE `account` SET `cash_balance` = `cash_balance` - #{amount}, "
            + "`frozen_balance` = `frozen_balance` + #{amount}, `version` = `version` + 1 "
            + "WHERE `id` = #{id} AND `cash_balance` >= #{amount}")
    int moveCashToFrozen(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 冻结 → 可用（取消解冻、结算退回）。
     */
    @Update("UPDATE `account` SET `cash_balance` = `cash_balance` + #{amount}, "
            + "`frozen_balance` = `frozen_balance` - #{amount}, `version` = `version` + 1 "
            + "WHERE `id` = #{id} AND `frozen_balance` >= #{amount}")
    int moveFrozenToCash(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 扣可用余额（结算时承租方付出逾期费与损耗费已含在冻结里，这里用于手工调整）。
     */
    @Update("UPDATE `account` SET `cash_balance` = `cash_balance` - #{amount}, `version` = `version` + 1 "
            + "WHERE `id` = #{id} AND `cash_balance` >= #{amount}")
    int deductCash(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 加可用余额。
     */
    @Update("UPDATE `account` SET `cash_balance` = `cash_balance` + #{amount}, `version` = `version` + 1 "
            + "WHERE `id` = #{id}")
    int addCash(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 外部账户扣款：允许扣成负数（负数代表"已从外部净流入的资金"，不是负债）。
     */
    @Update("UPDATE `account` SET `cash_balance` = `cash_balance` - #{amount}, `version` = `version` + 1 "
            + "WHERE `id` = #{id} AND `owner_type` = 'EXTERNAL'")
    int deductExternal(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 扣冻结余额（结算时把冻结里的钱划走）。
     */
    @Update("UPDATE `account` SET `frozen_balance` = `frozen_balance` - #{amount}, `version` = `version` + 1 "
            + "WHERE `id` = #{id} AND `frozen_balance` >= #{amount}")
    int deductFrozen(@Param("id") Long id, @Param("amount") BigDecimal amount);
}
