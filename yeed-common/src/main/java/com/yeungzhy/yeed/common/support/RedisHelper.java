package com.yeungzhy.yeed.common.support;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 基于 {@link StringRedisTemplate} 封装常用的 Key / String 类型操作<p>
 *
 * 关键参数校验与异常捕获，保证调用方在异常情况下能拿到安全默认值，避免由于 Redis 抖动直接导致业务流程中断
 */
@Slf4j
@Component
public class RedisHelper {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /* -------------------key相关操作--------------------- */

    /**
     * 删除单个key
     *
     * @param key 键，不能为空
     */
    public void delete(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.error("删除单个键异常, key={}", key, e);
        }
    }

    /**
     * 批量删除key
     *
     * @param keys 键集合，不能为空
     */
    public void delete(Collection<String> keys) {
        try {
            stringRedisTemplate.delete(keys);
        } catch (Exception e) {
            log.error("批量删除键异常, keys={}", keys, e);
        }
    }

    /**
     * 判断key是否存在
     *
     * @param key 键
     * @return true 存在；false 不存在、key为空或异常
     */
    public Boolean hasKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        try {
            return stringRedisTemplate.hasKey(key);
        } catch (Exception e) {
            log.error("判断键是否存在异常, key={}", key, e);
            return false;
        }
    }

    /**
     * 设置过期时间
     *
     * @param key     键，不能为空
     * @param timeout 过期时间，必须大于 0
     * @param unit    时间单位，不能为空
     * @return true 设置成功；false 参数非法或异常
     */
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        if (!StringUtils.hasText(key) || timeout <= 0 || unit == null) {
            return false;
        }
        try {
            return stringRedisTemplate.expire(key, timeout, unit);
        } catch (Exception e) {
            log.error("设置过期时间异常, key={}, timeout={}, unit={}", key, timeout, unit, e);
            return false;
        }
    }

    /**
     * 设置过期时间
     *
     * @param key  键，不能为空
     * @param date 失效时间点，不能为空
     * @return true 设置成功；false 参数非法或异常
     */
    public Boolean expireAt(String key, Date date) {
        if (!StringUtils.hasText(key) || date == null) {
            return false;
        }
        try {
            return stringRedisTemplate.expireAt(key, date);
        } catch (Exception e) {
            log.error("设置过期时间异常, key={}, date={}", key, date, e);
            return false;
        }
    }

    /**
     * 返回 key 的剩余的过期时间
     *
     * @param key  键，不能为空
     * @param unit 时间单位，不能为空
     * @return 剩余时间；异常或参数非法返回 0
     */
    public Long getExpire(String key, TimeUnit unit) {
        if (!StringUtils.hasText(key) || unit == null) {
            return 0L;
        }
        try {
            return stringRedisTemplate.getExpire(key, unit);
        } catch (Exception e) {
            log.error("返回键的剩余的过期时间异常, key={}, unit={}", key, unit, e);
            return 0L;
        }
    }

    /**
     * 返回 key 的剩余的过期时间（默认单位：秒）
     *
     * @param key 键，不能为空
     * @return 剩余时间(秒)；异常或参数非法返回 0
     */
    public Long getExpire(String key) {
        return getExpire(key, TimeUnit.SECONDS);
    }

    /* -------------------String相关操作--------------------- */

    /**
     * 设置指定 key 的值
     *
     * @param key   键，不能为空
     * @param value 值
     */
    public void set(String key, String value) {
        try {
            stringRedisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.error("设置指定键的值异常, key={}", key, e);
        }
    }

    /**
     * 获取指定 key 的值
     *
     * @param key 键
     * @return 值；key为空、不存在或异常返回 null
     */
    public String get(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("获取指定键的值异常, key={}", key, e);
            return null;
        }
    }

    /**
     * 获取指定 key 的值，并转换为 Long 类型<p>
     *
     * 内部调用 {@link #get(String)} 获取字符串后进行类型转换。
     *
     * @param key 键
     * @return Long 值；key为空、不存在、非数字类型或异常时返回 Long.MAX_VALUE
     */
    public Long getLong(String key, long defaultValue) {
        String value = get(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.error("获取指定键的Long值异常, key={}, value={}", key, value, e);
        }
        return defaultValue;
    }

    /**
     * 将值 value 关联到 key ，并将 key 的过期时间设为 timeout
     *
     * @param key     键，不能为空
     * @param value   值
     * @param timeout 过期时间，必须大于 0
     * @param unit    时间单位，不能为空
     */
    public void set(String key, String value, long timeout, TimeUnit unit) {
        if (!StringUtils.hasText(key) || timeout <= 0 || unit == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            log.error("设置指定键的值和过期时间异常, key={}, timeout={}, unit={}", key, timeout, unit, e);
        }
    }

    /**
     * 只有在 key 不存在时设置 key 的值
     *
     * @param key   键，不能为空
     * @param value 值
     * @return 之前已经存在返回false，不存在返回true；参数非法或异常返回false
     */
    public boolean setIfAbsent(String key, String value) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, value));
        } catch (Exception e) {
            log.error("当键不存在时设置值异常, key={}", key, e);
            return false;
        }
    }

    /**
     * 只有在 key 不存在时设置 key 的值，并设置过期时间<p>
     *
     * 对应 Redis 命令: SET key value NX EX/PX timeout
     *
     * @param key     键，不能为空
     * @param value   值
     * @param timeout 过期时间，必须大于 0
     * @param unit    时间单位，不能为空
     * @return true 设置成功；false key已存在、参数非法或异常
     */
    public boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
        if (!StringUtils.hasText(key) || timeout <= 0 || unit == null) {
            return false;
        }
        try {
            Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.error("当键不存在时设置值过期时间异常, key={}, timeout={}, unit={}", key, timeout, unit, e);
            return false;
        }
    }


    /**
     * 增加(自增长), 负数则为自减
     *
     * @param key       键，不能为空
     * @param increment 增量
     * @return 自增后的值；异常或参数非法返回 0
     */
    public Long incrBy(String key, long increment) {
        try {
            return stringRedisTemplate.opsForValue().increment(key, increment);
        } catch (Exception e) {
            log.error("自增异常, key={}, increment={}", key, increment, e);
            return 0L;
        }
    }

    /*-------------------原子性自增(带过期)--------------------- */

    /**
     * Lua 脚本：如果 key 不存在，则进行初始化自增并设置过期时间；如果已存在，则仅自增
     * <li>KEYS[1] = 键</li>
     * <li>ARGV[1] = 增量</li>
     * <li>ARGV[2] = 过期时间(秒)</li>
     */
    private static final String INCR_BY_WITH_EXPIRE_LUA_SCRIPT =
            "local exists = redis.call('exists', KEYS[1]) " +
            "local current = redis.call('incrby', KEYS[1], ARGV[1]) " +
            "if exists == 0 then " +
            "   redis.call('expire', KEYS[1], ARGV[2]) " +
            "end " +
            "return current";

    /**
     * 原子性自增：如果 key 不存在，则自增并设置过期时间；如果 key 已存在，仅自增<p>
     *
     * 解决并发场景下 hasKey() 与 incrBy() 之间的竞态条件
     *
     * @param key        键，不能为空
     * @param increment  增量
     * @param expireTime 过期时间（仅当 key 首次创建时生效），必须大于 0
     * @param unit       时间单位，不能为空
     * @return 自增后的值；参数非法或异常返回 0
     */
    public Long incrByWithExpire(String key, long increment, long expireTime, TimeUnit unit) {
        if (!StringUtils.hasText(key) || expireTime <= 0 || unit == null) {
            return 0L;
        }
        // 将时间统一转换为秒（如果需要支持毫秒级，可在 Lua 脚本中使用 pexpire）
        long expireSeconds = unit.toSeconds(expireTime);

        try {
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(INCR_BY_WITH_EXPIRE_LUA_SCRIPT, Long.class);
            // 执行 Lua 脚本
            return stringRedisTemplate.execute(
                    redisScript,
                    Collections.singletonList(key),
                    String.valueOf(increment),
                    String.valueOf(expireSeconds)
            );
        } catch (Exception e) {
            log.error("带过期时间的自增异常, key={}, increment={}, expireTime={}", key, increment, expireTime, e);
            return 0L;
        }
    }



    /* -------------------分布式锁相关操作--------------------- */

    /**
     * 释放锁的 Lua 脚本<p>
     * 逻辑：如果 key 对应的 value 等于传入的 requestId，则删除 key（释放锁），否则返回 0<p>
     * 使用 Lua 脚本保证了「判断 + 删除」的原子性，避免误删其他线程的锁
     */
    private static final String RELEASE_LOCK_LUA_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('del', KEYS[1]) " +
            "else " +
            "return 0 " +
            "end";

    /**
     * 尝试获取分布式锁 (非阻塞)<p>
     *
     * 使用 SET key value NX PX timeout 原子操作。<p>
     * value 必须设置为当前线程的唯一标识 (如 UUID)，用于安全释放锁。
     *
     * @param lockKey    锁的键，不能为空
     * @param requestId  请求的唯一标识 (通常使用 UUID)，用于标识锁的持有者，不能为空
     * @param expireTime 锁的过期时间，必须大于 0 (防止死锁)
     * @param unit       时间单位，不能为空
     * @return true 获取锁成功；false 获取锁失败(锁已被占用)或参数非法/异常
     */
    public boolean tryLock(String lockKey, String requestId, long expireTime, TimeUnit unit) {
        if (!StringUtils.hasText(lockKey) || !StringUtils.hasText(requestId) || expireTime <= 0 || unit == null) {
            return false;
        }
        try {
            // 对应 Redis 命令: SET lockKey requestId NX EX/PX expireTime
            Boolean isLocked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, requestId, expireTime, unit);
            return Boolean.TRUE.equals(isLocked);
        } catch (Exception e) {
            log.error("尝试获取分布式锁异常, lockKey={}, requestId={}", lockKey, requestId, e);
            return false;
        }
    }

    /**
     * 释放分布式锁<p>
     *
     * 通过执行 Lua 脚本来保证「验证 requestId 与 删除 key」的原子性<p>
     * 只有当 Redis 中存的 value 与传入的 requestId 一致时，才会删除 key，避免误删
     *
     * @param lockKey   锁的键，不能为空
     * @param requestId 请求的唯一标识，必须与加锁时传入的一致，不能为空
     * @return true 释放成功；false 释放失败(锁不属于当前线程/锁已过期)或参数非法/异常
     */
    public boolean releaseLock(String lockKey, String requestId) {
        if (!StringUtils.hasText(lockKey) || !StringUtils.hasText(requestId)) {
            return false;
        }
        try {
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(RELEASE_LOCK_LUA_SCRIPT, Long.class);
            // 执行 Lua 脚本
            Long result = stringRedisTemplate.execute(
                    redisScript,
                    Collections.singletonList(lockKey),
                    requestId
            );
            // 返回 1 表示删除成功，返回 0 表示锁不存在或 requestId 不匹配
            return Objects.equals(result, 1L);
        } catch (Exception e) {
            log.error("释放分布式锁异常, lockKey={}, requestId={}", lockKey, requestId, e);
            return false;
        }
    }


}
