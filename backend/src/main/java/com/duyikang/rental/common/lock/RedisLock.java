package com.duyikang.rental.common.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

/**
 * 基于 Redis 的分布式互斥锁（K2 第二层防线）。
 *
 * <p><b>为什么不是 SETNX + DEL</b>：SETNX 成功后如果没设过期时间，进程崩在中间这个 key 就永不过期；
 * 先 SETNX 再 EXPIRE 又有中间窗口。所以这里用 {@code SET key value NX PX ttl} 一条命令完成
 * "占位 + 过期"，由 Redis 保证原子性。</p>
 *
 * <p><b>为什么删除要写 Lua</b>：释放时必须先比对 value 再删。如果直接 DEL，
 * 会出现"锁已过期被 B 拿到，A 迟到的 finally 把 B 的锁删掉"这种典型误删。
 * Lua 脚本在 Redis 里单线程执行，"比对 + 删除"因此是原子的。</p>
 *
 * <p>锁只解决"同一物品并发下单"的排队问题，<b>不承担数据正确性</b>：真正防超卖的是
 * {@code order_occupancy} 上的唯一索引。锁过期、Redis 抖动、甚至整台 Redis 挂掉，
 * 最坏结果是并发请求都走到插入那一步，被唯一索引拦下转 4090，仍不会出现同一时段两单。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLock {

    /**
     * 只有 value 相等才删除。返回 1 表示删掉了自己的锁，0 表示锁已不属于自己（过期后被别人拿到）。
     */
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 尝试加锁，不阻塞。
     *
     * @param key   锁名，如 {@code lock:item:2}
     * @param value 本次持有的随机值，释放时用它证明"锁还是我的"
     * @param ttl   过期时间；必须给，否则进程崩溃会留下永久锁
     * @return true 表示加锁成功
     */
    public boolean tryLock(String key, String value, Duration ttl) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 释放锁。即使 value 不匹配也不会抛异常——释放锁失败不该影响主流程的结果，
     * 最多让这把锁挂到 TTL 到期（TTL 只有 10 秒）。
     */
    public void unlock(String key, String value) {
        try {
            Long released = redisTemplate.execute(UNLOCK, Collections.singletonList(key), value);
            if (released != null && released == 0L) {
                log.warn("释放锁时发现锁已不属于自己（可能已过期）：key={}", key);
            }
        } catch (RuntimeException e) {
            log.warn("释放锁失败（锁会在 TTL 到期后自动消失）：key={}", key, e);
        }
    }
}
