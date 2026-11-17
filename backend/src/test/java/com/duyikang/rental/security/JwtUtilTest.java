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
        // 必须改签名段的"首"字符：32 字节 HMAC 的 base64url 末字符含 2 个无关位，
        // 改末字符可能解码出完全相同的签名字节，篡改就白改了（曾因此偶发失败）。
        String tampered = tamperSignature(token);

        assertNull(jwtUtil.resolve(tampered));
    }

    /** 改签名段首字符：32 字节 HMAC 的 base64url 末字符含 2 个无关位，改末字符可能"改了等于没改"。 */
    private static String tamperSignature(String token) {
        String[] parts = token.split("\\.");
        char first = parts[2].charAt(0);
        char replaced = first == 'A' ? 'B' : 'A';
        return parts[0] + "." + parts[1] + "." + replaced + parts[2].substring(1);
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
