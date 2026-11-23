package com.duyikang.rental.module.wallet.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 管理端余额调整入参（正数加款、负数扣款，必须写理由）。
 *
 * @param amount 调整金额
 * @param remark 调整理由（进分录 remark，便于审计）
 * @author duyikang
 */
public record AdminAdjustDTO(
        @NotNull(message = "调整金额不能为空")
        @Digits(integer = 8, fraction = 2, message = "金额最多两位小数")
        BigDecimal amount,
        @NotBlank(message = "请填写调整理由")
        @Size(max = 200, message = "理由不超过 200 字")
        String remark) {
}
