package com.duyikang.rental.security;

import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

/**
 * Spring Security 6 配置。
 *
 * <p>要点：<br>
 * 1. 无状态（JWT），关 CSRF、关 formLogin、关 httpBasic；<br>
 * 2. 白名单：认证接口、Druid 监控页、<b>/uploads 静态图片</b>、错误页；其余一律需登录；<br>
 * 3. 未登录返回 JSON 1001，而不是默认的 302 跳转 / 401 空响应；<br>
 * 4. <b>必须放行 /druid/**</b>，否则监控页被 401 拦死（PROJECT_RULES.md §4.8 D9）。</p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/refresh",
                                "/druid/**",
                                // 物品图片必须匿名可读：<img src> 无法携带 Authorization 头。
                                // 这里只暴露 /uploads 下的静态文件，不含任何业务数据（PROJECT_RULES.md §4.6 F1 之外的补充）
                                "/uploads/**",
                                "/error",
                                "/favicon.ico"
                        ).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((request, response, e) ->
                                writeJson(response, resolveAuthError(request)))
                        .accessDeniedHandler((request, response, e) ->
                                writeJson(response, ErrorCode.FORBIDDEN))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 未登录时的返回码：默认 1001；若 {@link JwtAuthenticationFilter} 解析 token 失败，
     * 则用它记下的原因（1003 非法 / 1004 过期），前端据此决定是「重新登录」还是「刷新 token」。
     */
    private ErrorCode resolveAuthError(jakarta.servlet.http.HttpServletRequest request) {
        Object attr = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        return attr instanceof ErrorCode errorCode ? errorCode : ErrorCode.UNAUTHORIZED;
    }

    private void writeJson(HttpServletResponse response, ErrorCode errorCode) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(errorCode)));
    }
}
