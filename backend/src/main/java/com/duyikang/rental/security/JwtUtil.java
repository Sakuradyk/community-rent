package com.duyikang.rental.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具。
 *
 * <p>jjwt 0.12.x API：签名用 {@code signWith(SecretKey)}，解析用
 * {@code Jwts.parser().verifyWith(key).build().parseSignedClaims(token)}。
 * <b>不要</b>照抄 0.9.x 的 {@code setSigningKey(String)} / {@code parserBuilder().setSigningKey()}
 * （AI 协作手册「版本陷阱 #4」）。</p>
 */
@Component
public class JwtUtil {

    /** HS256 要求密钥不短于 32 字节，yml 里的 secret 必须够长。 */
    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expire-minutes:720}") long expireMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireMinutes * 60L * 1000L;
    }

    /**
     * 生成 token。
     *
     * @param userId   用户主键
     * @param username 登录名
     * @param role     角色 USER / ADMIN
     */
    public String generate(Long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析 token。
     *
     * @throws io.jsonwebtoken.JwtException 签名错误、过期、格式非法时抛出，由调用方处理
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从 token 还原登录主体；token 非法时返回 null（不抛异常，便于过滤器直接放行给 Security 判定）。 */
    public LoginUser resolve(String token) {
        try {
            Claims claims = parse(token);
            Long userId = Long.valueOf(claims.getSubject());
            String username = claims.get("username", String.class);
            String role = claims.get("role", String.class);
            return new LoginUser(userId, username, role);
        } catch (Exception e) {
            return null;
        }
    }
}
