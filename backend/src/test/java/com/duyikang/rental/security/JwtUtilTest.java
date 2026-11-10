package com.duyikang.rental.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * JWT 工具单元测试（纯单元测试，不启动 Spring 容器）。
 */
class JwtUtilTest {

    private static final String SECRET = "rental-platform-jwt-secret-key-2026-please-change-before-production";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 720);

    @Test
    @DisplayName("generate与resolve_合法token_能还原用户与角色")
    void generateAndResolve_validToken_restoresUser() {
        String token = jwtUtil.generate(1001L, "duyikang", "USER");

        LoginUser loginUser = jwtUtil.resolve(token);

        assertNotNull(loginUser);
        assertEquals(1001L, loginUser.userId());
        assertEquals("duyikang", loginUser.username());
        assertEquals("USER", loginUser.role());
    }

    @Test
    @DisplayName("resolve_篡改签名_返回null而不抛异常")
    void resolve_tamperedToken_returnsNull() {
        String token = jwtUtil.generate(1001L, "duyikang", "USER");
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
    @DisplayName("resolve_已过期token_返回null")
    void resolve_expiredToken_returnsNull() {
        JwtUtil expiredUtil = new JwtUtil(SECRET, -1L);
        String token = expiredUtil.generate(1001L, "duyikang", "USER");

        assertNull(jwtUtil.resolve(token));
    }
}
