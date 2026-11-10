package com.duyikang.rental.security;

/**
 * 登录态主体，存入 SecurityContext。
 *
 * <p>约定（PROJECT_RULES.md §7）：当前用户一律从 SecurityContext 取，
 * <b>禁止</b>信任请求参数或请求体里的 userId。</p>
 *
 * @param userId   用户主键
 * @param username 登录名
 * @param role     角色（USER / ADMIN），用于 @PreAuthorize 判断
 */
public record LoginUser(Long userId, String username, String role) {
}
