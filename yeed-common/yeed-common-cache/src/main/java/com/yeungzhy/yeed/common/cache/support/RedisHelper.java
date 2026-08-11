package com.yeungzhy.yeed.common.cache.support;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RList;
import org.redisson.api.RMap;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 基于 {@link RedissonClient} 封装常用的缓存操作
 *
 * <p>关键参数校验与异常捕获，保证调用方在异常情况下能拿到安全默认值，
 * 避免由于 Redis 抖动直接导致业务流程中断
 *
 * <p>分布式锁（RLock）、限流（RRateLimiter）、发布订阅（RTopic）等能力不在此封装，
 * 业务代码直接注入 {@link RedissonClient} 使用
 *
 * <p>通过 {@link com.yeungzhy.yeed.common.cache.config.RedisConfig} 注册为 Spring Bean
 *
 * @author yeungzhy
 */
@Slf4j
public class RedisHelper {

    private final RedissonClient redissonClient;

    public RedisHelper(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }


    /* ==================== 对象存取（RBucket）===================== */

    /**
     * 设置指定 key 的值
     *
     * @param key   键，不能为空
     * @param value 值
     */
    public void set(String key, Object value) {
        try {
            redissonClient.getBucket(key).set(value);
        } catch (Exception e) {
            log.error("Redis set异常, key={}", key, e);
        }
    }

    /**
     * 设置指定 key 的值和过期时间
     *
     * @param key     键，不能为空
     * @param value   值
     * @param timeout 过期时间，必须大于 0
     */
    public void set(String key, Object value, Duration timeout) {
        if (!StringUtils.hasText(key) || timeout == null || timeout.isNegative() || timeout.isZero()) {
            return;
        }
        try {
            redissonClient.getBucket(key).set(value, timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Redis set异常, key={}, timeout={}", key, timeout, e);
        }
    }

    /**
     * 设置指定 key 的值和过期时间
     *
     * @param key     键，不能为空
     * @param value   值
     * @param timeout 过期时间，必须大于 0
     * @param unit    时间单位，不能为空
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        set(key, value, Duration.of(timeout, unit.toChronoUnit()));
    }

    /**
     * 获取指定 key 的值
     *
     * @param key 键
     * @return 值；key为空、不存在或异常返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        try {
            return (T) redissonClient.getBucket(key).get();
        } catch (Exception e) {
            log.error("Redis get异常, key={}", key, e);
            return null;
        }
    }

    /**
     * 只有在 key 不存在时设置 key 的值
     *
     * @param key   键，不能为空
     * @param value 值
     * @return true 设置成功；false key已存在或异常
     */
    public boolean setIfAbsent(String key, Object value) {
        try {
            return redissonClient.getBucket(key).trySet(value);
        } catch (Exception e) {
            log.error("Redis setIfAbsent异常, key={}", key, e);
            return false;
        }
    }

    /**
     * 只有在 key 不存在时设置 key 的值和过期时间
     *
     * @param key     键，不能为空
     * @param value   值
     * @param timeout 过期时间，必须大于 0
     * @return true 设置成功；false key已存在或异常
     */
    public boolean setIfAbsent(String key, Object value, Duration timeout) {
        if (!StringUtils.hasText(key) || timeout == null || timeout.isNegative() || timeout.isZero()) {
            return false;
        }
        try {
            return redissonClient.getBucket(key).trySet(value, timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Redis setIfAbsent异常, key={}, timeout={}", key, timeout, e);
            return false;
        }
    }

    /**
     * 只有在 key 不存在时设置 key 的值和过期时间
     *
     * @param key     键，不能为空
     * @param value   值
     * @param timeout 过期时间，必须大于 0
     * @param unit    时间单位，不能为空
     * @return true 设置成功；false key已存在或异常
     */
    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        return setIfAbsent(key, value, Duration.of(timeout, unit.toChronoUnit()));
    }


    /* ==================== 批量操作（RBuckets）=================== */

    /**
     * 批量获取多个 key 的值（一次网络往返）
     *
     * @param keys 键集合，不能为空
     * @return Map&lt;key, value&gt;；异常或参数非法返回空集合
     */
    @SuppressWarnings("unchecked")
    public <T> Map<String, T> getBuckets(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            String[] keyArray = keys.toArray(new String[0]);
            return (Map<String, T>) redissonClient.getBuckets().get(keyArray);
        } catch (Exception e) {
            log.error("Redis getBuckets异常, keys={}", keys, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 批量设置多个 key 的值（一次网络往返）
     *
     * @param map 键值对集合，不能为空
     */
    public void setBuckets(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return;
        }
        try {
            redissonClient.getBuckets().set(map);
        } catch (Exception e) {
            log.error("Redis setBuckets异常", e);
        }
    }


    /* ==================== List 操作（RList）===================== */

    /**
     * 将 List 数据放入缓存（先清空再写入）
     *
     * @param key      键，不能为空
     * @param dataList 数据列表
     */
    public <T> void setList(String key, List<T> dataList) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        try {
            RList<T> list = redissonClient.getList(key);
            list.clear();
            if (dataList != null && !dataList.isEmpty()) {
                list.addAll(dataList);
            }
        } catch (Exception e) {
            log.error("Redis setList异常, key={}", key, e);
        }
    }

    /**
     * 获取 List 缓存
     *
     * @param key 键
     * @return List；异常或不存在返回空集合
     */
    public <T> List<T> getList(String key) {
        try {
            RList<T> list = redissonClient.getList(key);
            return new ArrayList<>(list);
        } catch (Exception e) {
            log.error("Redis getList异常, key={}", key, e);
            return Collections.emptyList();
        }
    }


    /* ==================== Set 操作（RSet）======================= */

    /**
     * 将 Set 数据放入缓存（先清空再写入）
     *
     * @param key     键，不能为空
     * @param dataSet 数据集合
     */
    public <T> void setSet(String key, Set<T> dataSet) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        try {
            RSet<T> set = redissonClient.getSet(key);
            set.clear();
            if (dataSet != null && !dataSet.isEmpty()) {
                set.addAll(dataSet);
            }
        } catch (Exception e) {
            log.error("Redis setSet异常, key={}", key, e);
        }
    }

    /**
     * 获取 Set 缓存
     *
     * @param key 键
     * @return Set；异常或不存在返回空集合
     */
    public <T> Set<T> getSet(String key) {
        try {
            RSet<T> set = redissonClient.getSet(key);
            return new HashSet<>(set);
        } catch (Exception e) {
            log.error("Redis getSet异常, key={}", key, e);
            return Collections.emptySet();
        }
    }


    /* ==================== Map 操作（RMap）======================= */

    /**
     * 将 Map 数据放入缓存（先清空再写入）
     *
     * @param key 键，不能为空
     * @param map 数据映射
     */
    public <K, V> void setMap(String key, Map<K, V> map) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        try {
            RMap<K, V> rMap = redissonClient.getMap(key);
            rMap.clear();
            if (map != null && !map.isEmpty()) {
                rMap.putAll(map);
            }
        } catch (Exception e) {
            log.error("Redis setMap异常, key={}", key, e);
        }
    }

    /**
     * 设置 Map 中指定 hashKey 的值
     *
     * @param key     键，不能为空
     * @param hashKey Map 内部的键，不能为空
     * @param value   值
     */
    public <K, V> void setMapValue(String key, K hashKey, V value) {
        if (!StringUtils.hasText(key) || hashKey == null) {
            return;
        }
        try {
            redissonClient.<K, V>getMap(key).put(hashKey, value);
        } catch (Exception e) {
            log.error("Redis setMapValue异常, key={}, hashKey={}", key, hashKey, e);
        }
    }

    /**
     * 获取 Map 中指定 hashKey 的值
     *
     * @param key     键
     * @param hashKey Map 内部的键
     * @return 值；异常或不存在返回 null
     */
    public <K, V> V getMapValue(String key, K hashKey) {
        try {
            return redissonClient.<K, V>getMap(key).get(hashKey);
        } catch (Exception e) {
            log.error("Redis getMapValue异常, key={}, hashKey={}", key, hashKey, e);
            return null;
        }
    }

    /**
     * 获取 Map 的所有 hashKey
     *
     * @param key 键
     * @return hashKey 集合；异常或不存在返回空集合
     */
    public <K> Set<K> getMapKeys(String key) {
        try {
            return redissonClient.<K, Object>getMap(key).keySet();
        } catch (Exception e) {
            log.error("Redis getMapKeys异常, key={}", key, e);
            return Collections.emptySet();
        }
    }

    /**
     * 批量获取 Map 中多个 hashKey 的值
     *
     * @param key      键
     * @param hashKeys Map 内部的键集合
     * @return 值集合；异常或不存在返回空集合
     */
    public <K, V> Collection<V> getMapValues(String key, Collection<K> hashKeys) {
        if (!StringUtils.hasText(key) || hashKeys == null || hashKeys.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            RMap<K, V> map = redissonClient.getMap(key);
            Map<K, V> result = map.getAll(new HashSet<>(hashKeys));
            return result.values();
        } catch (Exception e) {
            log.error("Redis getMapValues异常, key={}", key, e);
            return Collections.emptyList();
        }
    }


    /* ==================== 原子计数器（RAtomicLong）============== */

    /**
     * 自增 1 并返回自增后的值
     *
     * @param key 键，不能为空
     * @return 自增后的值；异常返回 0
     */
    public Long incrAndGet(String key) {
        try {
            return redissonClient.getAtomicLong(key).incrementAndGet();
        } catch (Exception e) {
            log.error("Redis incrAndGet异常, key={}", key, e);
            return 0L;
        }
    }

    /**
     * 自减 1 并返回自减后的值
     *
     * @param key 键，不能为空
     * @return 自减后的值；异常返回 0
     */
    public Long decrAndGet(String key) {
        try {
            return redissonClient.getAtomicLong(key).decrementAndGet();
        } catch (Exception e) {
            log.error("Redis decrAndGet异常, key={}", key, e);
            return 0L;
        }
    }

    /**
     * 增加指定增量并返回增加后的值（负数则为自减）
     *
     * @param key       键，不能为空
     * @param increment 增量
     * @return 增加后的值；异常返回 0
     */
    public Long incrBy(String key, long increment) {
        try {
            return redissonClient.getAtomicLong(key).addAndGet(increment);
        } catch (Exception e) {
            log.error("Redis incrBy异常, key={}, increment={}", key, increment, e);
            return 0L;
        }
    }

    /**
     * 获取当前原子计数器的值
     *
     * @param key 键
     * @return 当前值；异常返回 0
     */
    public Long getAtomicValue(String key) {
        try {
            return redissonClient.getAtomicLong(key).get();
        } catch (Exception e) {
            log.error("Redis getAtomicValue异常, key={}", key, e);
            return 0L;
        }
    }


    /* ==================== Key 操作 =============================== */

    /**
     * 删除单个 key
     *
     * @param key 键，不能为空
     */
    public void delete(String key) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        try {
            redissonClient.getBucket(key).delete();
        } catch (Exception e) {
            log.error("Redis delete异常, key={}", key, e);
        }
    }

    /**
     * 批量删除 key
     *
     * @param keys 键集合，不能为空
     */
    public void delete(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        try {
            redissonClient.getKeys().delete(keys.toArray(new String[0]));
        } catch (Exception e) {
            log.error("Redis批量delete异常, keys={}", keys, e);
        }
    }

    /**
     * 判断 key 是否存在
     *
     * @param key 键
     * @return true 存在；false 不存在、key为空或异常
     */
    public Boolean hasKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        try {
            return redissonClient.getBucket(key).isExists();
        } catch (Exception e) {
            log.error("Redis hasKey异常, key={}", key, e);
            return false;
        }
    }

    /**
     * 设置过期时间
     *
     * @param key     键，不能为空
     * @param timeout 过期时间，必须大于 0
     * @return true 设置成功；false 参数非法或异常
     */
    public Boolean expire(String key, Duration timeout) {
        if (!StringUtils.hasText(key) || timeout == null || timeout.isNegative() || timeout.isZero()) {
            return false;
        }
        try {
            return redissonClient.getBucket(key).expire(timeout);
        } catch (Exception e) {
            log.error("Redis expire异常, key={}, timeout={}", key, timeout, e);
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
        return expire(key, Duration.of(timeout, unit.toChronoUnit()));
    }

    /**
     * 设置过期时间点
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
            return redissonClient.getBucket(key).expire(date.toInstant());
        } catch (Exception e) {
            log.error("Redis expireAt异常, key={}, date={}", key, date, e);
            return false;
        }
    }

    /**
     * 返回 key 的剩余过期时间（默认单位：秒）
     *
     * @param key 键
     * @return 剩余时间(秒)；-1 表示永不过期，-2 表示 key 不存在，异常返回 0
     */
    public Long getExpire(String key) {
        return getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 返回 key 的剩余过期时间
     *
     * @param key  键
     * @param unit 时间单位
     * @return 剩余时间；-1 表示永不过期，-2 表示 key 不存在，异常或参数非法返回 0
     */
    public Long getExpire(String key, TimeUnit unit) {
        if (!StringUtils.hasText(key) || unit == null) {
            return 0L;
        }
        try {
            long millis = redissonClient.getBucket(key).remainTimeToLive();
            // -1 表示永不过期，-2 表示 key 不存在，原样返回
            if (millis < 0) {
                return millis;
            }
            return unit.convert(millis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Redis getExpire异常, key={}", key, e);
            return 0L;
        }
    }

}
