package com.duyikang.rental.security;

import com.duyikang.rental.common.constant.ErrorCode;
import org.junit.jupiter.api.AfterEach;
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

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 720);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("doFilter_合法token_建立登录态且不写失败原因")
    void doFilter_validToken_setsAuthenticationWithoutError() throws Exception {
        String token = jwtUtil.generate(1001L, "duyikang", "USER");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/items");
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE));
    }

    @Test
    @DisplayName("doFilter_过期token_记录TOKEN_EXPIRED且不建立登录态")
    void doFilter_expiredToken_recordsTokenExpired() throws Exception {
        String token = new JwtUtil(SECRET, -1L).generate(1001L, "duyikang", "USER");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/items");
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(ErrorCode.TOKEN_EXPIRED, request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE));
    }

    @Test
    @DisplayName("doFilter_伪造token_记录TOKEN_INVALID")
    void doFilter_tamperedToken_recordsTokenInvalid() throws Exception {
        String token = jwtUtil.generate(1001L, "duyikang", "USER");
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/items");
        request.addHeader("Authorization", "Bearer " + tampered);

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(ErrorCode.TOKEN_INVALID, request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE));
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
