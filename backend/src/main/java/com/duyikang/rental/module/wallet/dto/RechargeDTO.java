package com.duyikang.rental.module.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 模拟充值入参。
 *
 * <p>上限不写在这里：上限来自 {@code sys_config['wallet.recharge.max']}，
 * 属于可配置的业务规则，写注解等于把配置硬编码进 DTO。</p>
 *
 * @param amount 充值金额（元，最多两位小数）
 * @author duyikang
 */
public record RechargeDTO(
        @NotNull(message = "充值金额不能为空")
        @DecimalMin(value = "0.01", message = "充值金额必须大于 0")
        @Digits(integer = 8, fraction = 2, message = "金额最多两位小数")
        BigDecimal amount) {
}
