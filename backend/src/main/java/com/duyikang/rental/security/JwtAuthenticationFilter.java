package com.duyikang.rental.security;

import com.duyikang.rental.common.constant.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器。
 *
 * <p>只负责「解析 token → 建立登录态」，<b>不做</b>鉴权决策；是否放行由
 * {@link SecurityConfig} 的 authorizeHttpRequests 决定。token 缺失或非法时不直接写响应，
 * 而是把失败原因放进 request attribute，交给 Security 的 EntryPoint 统一输出
 * —— 这样 1001 / 1003 / 1004 的区分只在一处（PROJECT_RULES.md §7.2）。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 认证失败原因的 request attribute 名，由 {@link SecurityConfig} 的 EntryPoint 读取。 */
    public static final String AUTH_ERROR_ATTRIBUTE = "rental.auth.error";

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length()).trim();
            JwtUtil.ParseResult result = jwtUtil.resolveWithReason(token);
            if (result.isOk()) {
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    List<SimpleGrantedAuthority> authorities =
                            List.of(new SimpleGrantedAuthority("ROLE_" + result.user().role()));
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(result.user(), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } else {
                // 不在此处写响应：交由 EntryPoint 统一输出，避免两处各写一份 JSON
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, result.error());
            }
        }
        chain.doFilter(request, response);
    }
}
