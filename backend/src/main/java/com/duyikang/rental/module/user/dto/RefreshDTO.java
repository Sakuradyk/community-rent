package com.duyikang.rental.module.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新令牌入参。
 *
 * <p>单独用一个 DTO 而不是从请求头读 refresh：refresh 与 access 走不同的入口，
 * 混在 Authorization 里会让过滤器无法区分两种令牌（PROJECT_RULES.md §7.2）。</p>
 *
 * @param refreshToken 刷新令牌（登录 / 刷新接口返回）
 */
public record RefreshDTO(
        @NotBlank(message = "刷新令牌不能为空")
        String refreshToken
) {
}
