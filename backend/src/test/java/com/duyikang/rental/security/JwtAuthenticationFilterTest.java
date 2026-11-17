package com.duyikang.rental.security;

import com.duyikang.rental.common.constant.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * JWT 过滤器的失败原因传递测试。
 *
 * <p>验证点：token 过期要能被区分成 1004、伪造要能被区分成 1003，而不是一律 1001 ——
 * 否则前端永远只能让用户重新登录（PROJECT_RULES.md §7.2）。</p>
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "rental-platform-jwt-secret-key-2026-please-change-before-production";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 120, 7);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil);

    // 同一 JVM 里 Spring 测试（AuthIntegrationTest）会把登录态放进线程本地的
    // SecurityContextHolder，若不清洗，"伪造 token 不建立登录态"这类断言会偶发失败。
    @BeforeEach
    void clearBefore() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("doFilter_合法token_建立登录态且不写失败原因")
    void doFilter_validToken_setsAuthenticationWithoutError() throws Exception {
        String token = jwtUtil.generateAccess(1001L, "duyikang", "USER");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/items");
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE));
    }

    @Test
    @DisplayName("doFilter_过期token_记录TOKEN_EXPIRED且不建立登录态")
    void doFilter_expiredToken_recordsTokenExpired() throws Exception {
        String token = new JwtUtil(SECRET, -1L, 7).generateAccess(1001L, "duyikang", "USER");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/items");
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(ErrorCode.TOKEN_EXPIRED, request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE));
    }

    @Test
    @DisplayName("doFilter_伪造token_记录TOKEN_INVALID")
    void doFilter_tamperedToken_recordsTokenInvalid() throws Exception {
        String token = jwtUtil.generateAccess(1001L, "duyikang", "USER");
        String tampered = tamperSignature(token);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/items");
        request.addHeader("Authorization", "Bearer " + tampered);

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(ErrorCode.TOKEN_INVALID, request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE));
    }

    /** 改签名段首字符：32 字节 HMAC 的 base64url 末字符含 2 个无关位，改末字符可能"改了等于没改"。 */
    private static String tamperSignature(String token) {
        String[] parts = token.split("\\.");
        char first = parts[2].charAt(0);
        char replaced = first == 'A' ? 'B' : 'A';
        return parts[0] + "." + parts[1] + "." + replaced + parts[2].substring(1);
    }
    @Test
    @DisplayName("doFilter_无Authorization头_不写失败原因由入口点默认1001")
    void doFilter_noHeader_leavesAttributeEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/items");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE));
    }
}
