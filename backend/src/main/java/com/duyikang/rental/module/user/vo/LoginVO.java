package com.duyikang.rental.module.user.vo;

/**
 * 登录 / 注册 / 刷新成功的返回体。
 *
 * <p>为什么一次返回两枚令牌：access 只有 2 小时，前端要在过期前用 refresh 换新；
 * 若分成两次请求，注册后就得再登录一次，体验割裂（PROJECT_RULES.md §7.2）。</p>
 *
 * @param accessToken  访问令牌，放进 `Authorization: Bearer xxx`
 * @param refreshToken 刷新令牌，只用于 `POST /api/auth/refresh`
 * @param expiresIn    access 有效期（秒），前端据此安排静默刷新
 * @param user         当前用户信息（免一次 `/api/users/me`）
 */
public record LoginVO(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserVO user
) {
}
