package com.duyikang.rental.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 重置密码入参（忘记密码）。
 *
 * <p>本项目没有短信/邮件通道（需求总结 §10 明确不做），因此采用<b>知识校验</b>：
 * 手机号 + 昵称必须与该账号一致；若该账号注册时选过社区，还必须选对社区。
 * 这是弱校验，风险与适用范围写在 {@code docs/接口文档.md} §1.9 与需求总结 §14.1。</p>
 *
 * <p>{@code communityId} 允许为空 —— 用户注册时本就可以不选社区，此时只校验前两项。</p>
 *
 * @param phone       已注册手机号
 * @param nickname    该手机号对应的昵称
 * @param communityId 该账号注册时选的社区（未设置过则忽略）
 * @param newPassword 新密码 6–20 位
 */
public record ResetPasswordDTO(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        @NotBlank(message = "昵称不能为空")
        @Size(min = 2, max = 16, message = "昵称长度 2-16 个字")
        String nickname,

        Long communityId,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 20, message = "密码长度 6-20 位")
        String newPassword
) {
}
