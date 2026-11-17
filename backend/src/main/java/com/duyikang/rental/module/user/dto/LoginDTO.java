package com.duyikang.rental.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 登录入参。
 *
 * <p>只支持手机号 + 密码：数据库唯一键 `uk_user_phone` 决定了登录名的形态，
 * 昵称可改，不能作为登录凭证（PROJECT_RULES.md §7）。</p>
 *
 * @param phone    手机号
 * @param password 明文密码
 */
public record LoginDTO(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        @NotBlank(message = "密码不能为空")
        String password
) {
}
