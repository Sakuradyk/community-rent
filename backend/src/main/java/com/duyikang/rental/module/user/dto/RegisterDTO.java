package com.duyikang.rental.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册入参。
 *
 * <p>为什么只收这四项：本课题不做短信验证码与实名认证（需求总结 §10），
 * 手机号只作为唯一登录名使用，因此不校验验证码；社区可选，
 * 便于"只想先逛逛"的用户不被卡住（列表页的社区优先档会自动退化为热度排序）。</p>
 *
 * @param phone       手机号，唯一登录名
 * @param password    明文密码，入库前 BCrypt 加密（不可逆）
 * @param nickname    昵称，同时作为 JWT 里的 username 展示名
 * @param communityId 所属社区主键，可为 null
 */
public record RegisterDTO(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 20, message = "密码长度需为 6-20 位")
        String password,

        @NotBlank(message = "昵称不能为空")
        @Size(min = 2, max = 16, message = "昵称长度需为 2-16 位")
        String nickname,

        Long communityId
) {
}
