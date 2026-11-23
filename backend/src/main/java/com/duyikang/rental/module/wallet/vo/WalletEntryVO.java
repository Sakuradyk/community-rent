package com.duyikang.rental.module.wallet.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包流水条目（用户视角：钱进还是钱出）。
 *
 * <p>同一个账户的「冻结」是内部移动，展示成「出（冻结）」更贴近用户直觉，
 * 因此这里给出 {@code direction} 与 {@code bucket}，由后端判定，前端不再推算。</p>
 *
 * @param id          分录 id
 * @param entryNo     分录号
 * @param action      动作码
 * @param actionLabel 动作中文
 * @param direction   IN 收入 / OUT 支出 / MOVE 内部移动
 * @param bucket      CASH 可用 / FROZEN 冻结
 * @param amount      金额
 * @param bizType     业务类型
 * @param bizId       业务主键
 * @param remark      说明
 * @param balanceAfter 该账户该桶的余额快照
 * @param createdAt   记账时间
 * @author duyikang
 */
public record WalletEntryVO(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String entryNo,
        String action,
        String actionLabel,
        String direction,
        String bucket,
        BigDecimal amount,
        String bizType,
        @JsonSerialize(using = ToStringSerializer.class) Long bizId,
        String remark,
        BigDecimal balanceAfter,
        LocalDateTime createdAt) {
}
