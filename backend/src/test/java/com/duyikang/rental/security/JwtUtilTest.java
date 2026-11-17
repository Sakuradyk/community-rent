package com.duyikang.rental.security;

import com.duyikang.rental.common.constant.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 工具单元测试（纯单元测试，不启动 Spring 容器）。
 *
 * <p>构造参数为（密钥, access 分钟, refresh 天数），与 {@code jwt.expire-minutes} /
 * {@code jwt.refresh-expire-days} 对应。</p>
 */
class JwtUtilTest {

    private static final String SECRET = "rental-platform-jwt-secret-key-2026-please-change-before-production";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 120, 7);

    @Test
    @DisplayName("generateAccess与resolve_合法token_能还原用户与角色")
    void generateAndResolve_validToken_restoresUser() {
        String token = jwtUtil.generateAccess(1001L, "duyikang", "USER");

        LoginUser loginUser = jwtUtil.resolve(token);

        assertNotNull(loginUser);
        assertEquals(1001L, loginUser.userId());
        assertEquals("duyikang", loginUser.username());
        assertEquals("USER", loginUser.role());
    }

    @Test
    @DisplayName("resolve_篡改签名_返回null而不抛异常")
    void resolve_tamperedToken_returnsNull() {
        String token = jwtUtil.generateAccess(1001L, "duyikang", "USER");
        // 改动签名段最后一个字符
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");

        assertNull(jwtUtil.resolve(tampered));
    }

    @Test
    @DisplayName("resolve_乱码字符串_返回null")
    void resolve_garbage_returnsNull() {
        assertNull(jwtUtil.resolve("not-a-jwt"));
    }

    @Test
    @DisplayName("resolve_已过期token_返回null且原因为TOKEN_EXPIRED")
    void resolve_expiredToken_returnsNull() {
        JwtUtil expiredUtil = new JwtUtil(SECRET, -1L, 7);
        String token = expiredUtil.generateAccess(1001L, "duyikang", "USER");

        assertNull(jwtUtil.resolve(token));
        // 过期与非法必须可区分：前端据此决定"刷新"还是"重新登录"
        assertEquals(ErrorCode.TOKEN_EXPIRED, jwtUtil.resolveWithReason(token).error());
        assertTrue(jwtUtil.accessExpiresInSeconds() > 0);
    }
}
