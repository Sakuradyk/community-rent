package com.duyikang.rental.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * refresh 令牌白名单存储（Redis）。
 *
 * <p>为什么要存：JWT 是自包含的，签出去就收不回来。登出、改密码、刷新轮换
 * 都需要「让旧令牌立刻失效」，而唯一的办法是服务端留一份白名单——
 * 只有当前有效的那一枚 {@code jti} 能换新令牌。</p>
 *
 * <p>存储结构：`auth:refresh:{userId}` → `{jti}`，TTL = refresh 有效期（7 天）。
 * 一个用户同一时刻只有一枚有效 refresh 令牌，刷新即覆盖（旧的自然失效）。</p>
 *
 * <p>为什么不用 String 集合存历史：毕设规模下"一用户一令牌"足够，
 * 且登出语义更简单（删 key 即可）。多端登录不在范围内（需求总结 §10）。</p>
 */
@Component
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public RefreshTokenStore(StringRedisTemplate redisTemplate,
                             @Value("${jwt.refresh-expire-days:7}") long refreshExpireDays) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofDays(refreshExpireDays);
    }

    /** 保存（覆盖）用户的当前 refresh 令牌编号。 */
    public void save(Long userId, String jti) {
        redisTemplate.opsForValue().set(keyOf(userId), jti, ttl);
    }

    /** 判断该 jti 是否仍是用户当前有效的 refresh 令牌。 */
    public boolean matches(Long userId, String jti) {
        if (userId == null || jti == null) {
            return false;
        }
        String current = redisTemplate.opsForValue().get(keyOf(userId));
        return jti.equals(current);
    }

    /** 登出 / 强制失效：删除白名单记录，此后旧 refresh 令牌一律拒绝。 */
    public void remove(Long userId) {
        redisTemplate.delete(keyOf(userId));
    }

    private String keyOf(Long userId) {
        return KEY_PREFIX + userId;
    }
}
