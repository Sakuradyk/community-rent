package com.duyikang.rental.security;

import com.duyikang.rental.common.constant.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
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
     * 解析结果：成功时 {@code user != null}、{@code error == null}；
     * 失败时 {@code user == null}、{@code error} 指明是「过期」还是「非法」。
     *
     * <p>为什么要区分：1001 表示「没带 token」，1004 表示「带过期了」，
     * 1003 表示「带了个假的」。前端据此决定「去登录」还是「刷新 token」，
     * 统一成 1001 就永远只能让用户重新登录（PROJECT_RULES.md §7.2）。</p>
     */
    public record ParseResult(LoginUser user, ErrorCode error) {

        public boolean isOk() {
            return user != null;
        }

        public static ParseResult success(LoginUser user) {
            return new ParseResult(user, null);
        }

        public static ParseResult failure(ErrorCode error) {
            return new ParseResult(null, error);
        }
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

    /**
     * 解析并给出失败原因，供过滤器和 Security EntryPoint 使用。
     *
     * <p>过期 → {@link ErrorCode#TOKEN_EXPIRED}(1004)；签名错/格式错/主体缺失 →
     * {@link ErrorCode#TOKEN_INVALID}(1003)。空字符串按非法处理（调用方应先判 null）。</p>
     */
    public ParseResult resolveWithReason(String token) {
        if (token == null || token.isBlank()) {
            return ParseResult.failure(ErrorCode.UNAUTHORIZED);
        }
        try {
            Claims claims = parse(token);
            Long userId = Long.valueOf(claims.getSubject());
            String username = claims.get("username", String.class);
            String role = claims.get("role", String.class);
            if (username == null || role == null) {
                return ParseResult.failure(ErrorCode.TOKEN_INVALID);
            }
            return ParseResult.success(new LoginUser(userId, username, role));
        } catch (ExpiredJwtException e) {
            return ParseResult.failure(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            return ParseResult.failure(ErrorCode.TOKEN_INVALID);
        }
    }

    /**
     * 从 token 还原登录主体；token 非法时返回 null。
     *
     * <p>只关心「能不能用」时用这个；需要告诉前端「为什么不能用」时用
     * {@link #resolveWithReason(String)}。</p>
     */
    public LoginUser resolve(String token) {
        return resolveWithReason(token).user();
    }
}
