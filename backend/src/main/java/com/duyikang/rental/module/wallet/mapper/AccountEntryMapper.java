package com.duyikang.rental.module.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duyikang.rental.module.wallet.entity.AccountEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 分录 Mapper。
 *
 * <p>只做插入与查询，不提供 update / delete —— 分录是账的证据，缺了就对不上账。</p>
 *
 * @author duyikang
 */
@Mapper
public interface AccountEntryMapper extends BaseMapper<AccountEntry> {

    /**
     * 当日已有分录条数，用于生成 {@code entry_no} 的 4 位当日序号。
     *
     * @param prefix 形如 {@code F20261123}
     * @return 已有条数
     */
    @Select("SELECT COUNT(*) FROM `account_entry` WHERE `entry_no` LIKE CONCAT(#{prefix}, '%')")
    int countByPrefix(String prefix);

    /**
     * 某笔业务是否已经入过账（幂等前置检查，给「解冻」这类可能被重复触发的动作兜底）。
     *
     * @param bizType 业务类型
     * @param bizId   业务主键
     * @param action  动作
     * @return 已存在的条数
     */
    @Select("SELECT COUNT(*) FROM `account_entry` WHERE `biz_type` = #{bizType} AND `biz_id` = #{bizId} "
            + "AND `action` = #{action}")
    int countByBiz(String bizType, Long bizId, String action);

    /**
     * 全站可用 + 冻结余额之和，必须为 0（守恒断言用）。
     */
    @Select("SELECT COALESCE(SUM(`cash_balance` + `frozen_balance`), 0) FROM `account`")
    BigDecimal sumAllBalances();
}
