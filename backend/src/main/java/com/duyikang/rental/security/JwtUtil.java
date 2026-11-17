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
 * JWT 工具（双令牌：access + refresh）。
 *
 * <p>jjwt 0.12.x API：签名用 {@code signWith(SecretKey)}，解析用
 * {@code Jwts.parser().verifyWith(key).build().parseSignedClaims(token)}。
 * <b>不要</b>照抄 0.9.x 的 {@code setSigningKey(String)} / {@code parserBuilder().setSigningKey()}
 * （AI 协作手册「版本陷阱 #4」）。</p>
 *
 * <p>为什么要有两种令牌（PROJECT_RULES.md §7.2）：access 只有 2 小时，泄露窗口小；
 * refresh 有 7 天但只能换令牌、不能访问业务接口。两者用 {@code typ} claim 区分，
 * <b>拿 access 去调刷新接口必须失败</b>（否则短有效期毫无意义）。</p>
 */
@Component
public class JwtUtil {

    /** access 令牌类型。 */
    public static final String TYPE_ACCESS = "access";
    /** refresh 令牌类型。 */
    public static final String TYPE_REFRESH = "refresh";

    private static final String CLAIM_TYPE = "typ";

    /** HS256 要求密钥不短于 32 字节，yml 里的 secret 必须够长。 */
    private final SecretKey key;
    private final long accessMillis;
    private final long refreshMillis;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expire-minutes:120}") long accessMinutes,
                   @Value("${jwt.refresh-expire-days:7}") long refreshDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessMillis = accessMinutes * 60L * 1000L;
        this.refreshMillis = refreshDays * 24L * 60L * 60L * 1000L;
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
     * refresh 令牌的解析结果。
     *
     * <p>refresh 只带 {@code sub}（用户主键）与 {@code jti}（令牌唯一编号），
     * <b>不带</b> username / role —— 刷新时要重新查库取最新角色，
     * 否则管理员被降权后还能靠旧 refresh 换出新 access。</p>
     */
    public record RefreshClaims(Long userId, String jti, ErrorCode error) {

        public boolean isOk() {
            return userId != null && error == null;
        }

        public static RefreshClaims success(Long userId, String jti) {
            return new RefreshClaims(userId, jti, null);
        }

        public static RefreshClaims failure(ErrorCode error) {
            return new RefreshClaims(null, null, error);
        }
    }

    /**
     * 生成 access 令牌。
     *
     * @param userId   用户主键
     * @param username 展示名（昵称）
     * @param role     角色 USER / ADMIN
     */
    public String generateAccess(Long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 生成 refresh 令牌。
     *
     * @param userId 用户主键
     * @param jti    令牌唯一编号，入库（Redis）后用于「一令牌一用」与登出失效
     */
    public String generateRefresh(Long userId, String jti) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(jti)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshMillis))
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
     * 解析并给出失败原因，按 access 类型校验（供过滤器与 Security EntryPoint 使用）。
     *
     * <p>过期 → {@link ErrorCode#TOKEN_EXPIRED}(1004)；签名错/格式错/主体缺失/类型不符 →
     * {@link ErrorCode#TOKEN_INVALID}(1003)。空字符串按「未登录」处理（1001）。</p>
     */
    public ParseResult resolveWithReason(String token) {
        if (token == null || token.isBlank()) {
            return ParseResult.failure(ErrorCode.UNAUTHORIZED);
        }
        try {
            Claims claims = parse(token);
            if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
                return ParseResult.failure(ErrorCode.TOKEN_INVALID);
            }
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
     * 解析 refresh 令牌：只校验「是不是 refresh 类型」，username / role 不参与。
     *
     * <p>拿 access 令牌调用 → {@link ErrorCode#TOKEN_INVALID}(1003)。</p>
     */
    public RefreshClaims resolveRefresh(String token) {
        if (token == null || token.isBlank()) {
            return RefreshClaims.failure(ErrorCode.UNAUTHORIZED);
        }
        try {
            Claims claims = parse(token);
            if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
                return RefreshClaims.failure(ErrorCode.TOKEN_INVALID);
            }
            Long userId = Long.valueOf(claims.getSubject());
            String jti = claims.getId();
            if (jti == null || jti.isBlank()) {
                return RefreshClaims.failure(ErrorCode.TOKEN_INVALID);
            }
            return RefreshClaims.success(userId, jti);
        } catch (ExpiredJwtException e) {
            return RefreshClaims.failure(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            return RefreshClaims.failure(ErrorCode.TOKEN_INVALID);
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

    /** access 令牌有效期（秒），用于登录响应的 {@code expiresIn}，前端据此提前刷新。 */
    public long accessExpiresInSeconds() {
        return accessMillis / 1000L;
    }
}
