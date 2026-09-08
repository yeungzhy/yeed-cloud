package com.yeungzhy.yeed.common.cache.support;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBatch;
import org.redisson.api.RBucket;
import org.redisson.api.RList;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 基于 {@link RedissonClient} 封装常用的缓存操作
 *
 * <p> 统一做参数校验与异常兜底：写方法参数非法直接忽略并返回 {@code false}，
 * 读方法参数非法或 Redis 异常记日志并返回安全默认值（null / 空集合 / 0 / false），
 * 不让 Redis 抖动中断业务流程
 *
 * <p> 无状态（只持有 {@link RedissonClient} 与默认 TTL），由
 * {@link com.yeungzhy.yeed.common.cache.config.RedisAutoConfiguration} 注册为单例，多线程共享安全
 *
 * <p> 写方法的 {@code timeout} 是三态的：
 * <ul>
 *   <li>不传：取默认 TTL（{@code yeed.cache.default-ttl}，见
 *   {@link com.yeungzhy.yeed.common.cache.config.CacheProperties}，默认 24 小时）
 *   <li>传 {@code null}：永久存储，仅适用于每次整体重建覆盖的重建型缓存，
 *   调用方需在注释里写明永久存储的理由
 *   <li>传负数 / 零：参数非法，不执行写操作
 * </ul>
 *
 * <p> TTL 作用于整个 key 而不是单个元素：对永久存储的 key 调用增量方法
 * （setMapValue / addSetValue / removeSetValue / listAdd / incr*）会把它降级为默认 TTL，
 * 这类场景必须显式传 {@code null}
 *
 * <p> 值走 Jackson 序列化，写入的对象需可被 Jackson 处理
 *
 * <p> 分布式锁只做薄封装：{@link #executeWithLock(String, Duration, Supplier)}
 * 统一「tryLock + finally 解锁」样板，锁 key 自动加 {@code yeed:lock:} 前缀（见
 * {@link com.yeungzhy.yeed.common.core.constant.CacheConstant}），
 * 参数非法时抛 {@link IllegalArgumentException} 而非静默无锁执行；
 * 限流、订阅监听器生命周期等其余能力直接注入 {@link RedissonClient} 使用
 *
 * @author yeungzhy
 * @since 2026-08-11
 */
@Slf4j
public class RedisHelper {

    /** 配置缺失时的兜底默认过期时间：24 小时 */
    private static final Duration FALLBACK_TTL = Duration.ofHours(24);

    /** 分布式锁 key 统一前缀，遵守 {@link com.yeungzhy.yeed.common.core.constant.CacheConstant} 命名规范 */
    private static final String LOCK_KEY_PREFIX = "yeed:lock:";

    private final RedissonClient redissonClient;

    /** 无参写方法采用的默认过期时间（由 CacheProperties 注入，见类注释 TTL 策略） */
    private final Duration defaultTtl;

    public RedisHelper(RedissonClient redissonClient, Duration defaultTtl) {
        this.redissonClient = redissonClient;
        // defaultTtl 为空或非法（负/零）时兜底为 24 小时，保证「不传 = 有默认过期时间」恒成立
        this.defaultTtl = defaultTtl != null && !defaultTtl.isNegative() && !defaultTtl.isZero()
                ? defaultTtl : FALLBACK_TTL;
    }

    /**
     * timeout 是否非法：null 合法（表示永久存储），负数 / 零非法
     */
    private static boolean isInvalidTtl(Duration timeout) {
        return timeout != null && (timeout.isNegative() || timeout.isZero());
    }


    // ======================== 对象存取（RBucket）=======================

    /**
     * 设置指定 key 的值，过期时间取默认 TTL（见类注释）
     *
     * @param key   键，为空直接返回 false
     * @param value 值，需可被 Jackson 序列化
     * @return true 写入成功；false 参数非法或 Redis 异常
     */
    public boolean set(String key, Object value) {
        return set(key, value, defaultTtl);
    }

    /**
     * 设置指定 key 的值和过期时间
     *
     * @param key     键，为空直接返回 false
     * @param value   值，需可被 Jackson 序列化
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储
     * @return true 写入成功；false 参数非法或 Redis 异常
     */
    public boolean set(String key, Object value, Duration timeout) {
        if (!StringUtils.hasText(key) || isInvalidTtl(timeout)) {
            return false;
        }
        try {
            RBucket<Object> bucket = redissonClient.getBucket(key);
            if (timeout != null) {
                bucket.set(value, timeout);
            } else {
                bucket.set(value);
            }
            return true;
        } catch (Exception e) {
            log.error("Redis set异常, key={}, timeout={}", key, timeout, e);
            return false;
        }
    }

    /**
     * 设置指定 key 的值和过期时间
     *
     * <p> 本重载表达不了「永久存储」（{@code long} 取不到 null），需要永久存储请用 Duration 重载
     *
     * @param key     键，为空直接返回 false
     * @param value   值，需可被 Jackson 序列化
     * @param timeout 过期时长，必须大于 0，否则按参数非法返回 false
     * @param unit    timeout 的单位，为空抛 NullPointerException
     * @return true 写入成功；false 参数非法或 Redis 异常
     */
    public boolean set(String key, Object value, long timeout, TimeUnit unit) {
        return set(key, value, Duration.of(timeout, unit.toChronoUnit()));
    }

    /**
     * 获取指定 key 的值
     *
     * @param key 键，为空直接返回 null
     * @return 值；key 为空、不存在或 Redis 异常返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        try {
            return (T) redissonClient.getBucket(key).get();
        } catch (Exception e) {
            log.error("Redis get异常, key={}", key, e);
            return null;
        }
    }

    /**
     * 只有在 key 不存在时设置 key 的值，过期时间取默认 TTL（见类注释）
     *
     * @param key   键，为空直接返回 false
     * @param value 值，需可被 Jackson 序列化
     * @return true 设置成功；false key 已存在、参数非法或 Redis 异常
     */
    public boolean setIfAbsent(String key, Object value) {
        return setIfAbsent(key, value, defaultTtl);
    }

    /**
     * 只有在 key 不存在时设置 key 的值和过期时间
     *
     * @param key     键，为空直接返回 false
     * @param value   值，需可被 Jackson 序列化
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储
     * @return true 设置成功；false key 已存在、参数非法或 Redis 异常
     */
    public boolean setIfAbsent(String key, Object value, Duration timeout) {
        if (!StringUtils.hasText(key) || isInvalidTtl(timeout)) {
            return false;
        }
        try {
            RBucket<Object> bucket = redissonClient.getBucket(key);
            if (timeout != null) {
                return bucket.setIfAbsent(value, timeout);
            }
            return bucket.setIfAbsent(value);
        } catch (Exception e) {
            log.error("Redis setIfAbsent异常, key={}, timeout={}", key, timeout, e);
            return false;
        }
    }

    /**
     * 只有在 key 不存在时设置 key 的值和过期时间
     *
     * @param key     键，为空直接返回 false
     * @param value   值，需可被 Jackson 序列化
     * @param timeout 过期时长，必须大于 0，否则按参数非法返回 false
     * @param unit    timeout 的单位，为空抛 NullPointerException
     * @return true 设置成功；false key 已存在、参数非法或 Redis 异常
     */
    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        return setIfAbsent(key, value, Duration.of(timeout, unit.toChronoUnit()));
    }

    /**
     * 获取并删除指定 key 的值（一次性消费场景：防重令牌、一次性凭证等）
     *
     * @param key 键，为空直接返回 null
     * @return 删除前的值；key 为空、不存在或 Redis 异常返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T getAndDelete(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        try {
            return (T) redissonClient.getBucket(key).getAndDelete();
        } catch (Exception e) {
            log.error("Redis getAndDelete异常, key={}", key, e);
            return null;
        }
    }


    // ======================== 批量操作（RBatch / RBuckets）=============

    /**
     * 批量获取多个 key 的值
     *
     * <p> 一次网络往返取回全部 key，数量上千时分批调用，避免单条命令拖慢 Redis
     * 结果是按 T 强转的，同一批 key 的值类型需一致，混入其他类型要等取值时才抛 ClassCastException
     *
     * @param keys 键集合，为空返回空 Map
     * @return key 到值的映射；参数非法或 Redis 异常返回空 Map
     */
    @SuppressWarnings("unchecked")
    public <T> Map<String, T> getBatch(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            String[] keyArray = keys.toArray(new String[0]);
            return (Map<String, T>) redissonClient.getBuckets().get(keyArray);
        } catch (Exception e) {
            log.error("Redis getBatch异常, keys={}", keys, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 批量设置多个 key 的值，过期时间取默认 TTL（见类注释）
     *
     * <p> 一次网络往返提交，其余约束见 {@link #setBatch(Map, Duration)}
     *
     * @param map 键值对集合，为空返回 false
     * @return true 写入成功；false 参数非法或 Redis 异常
     */
    public boolean setBatch(Map<String, Object> map) {
        return setBatch(map, defaultTtl);
    }

    /**
     * 批量设置多个 key 的值和过期时间
     *
     * <p> 走 RBatch 一次网络往返提交，每条命令自带 TTL，不写完再逐 key expire，
     * 避免中途失败留下「部分 key 有 TTL、部分没有」的脏状态
     *
     * <p> 整批执行非原子，中途失败可能只写入一部分，业务需能容忍；key 数量上千时分批调用
     *
     * @param map     键值对集合，为空返回 false
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储
     * @return true 写入成功；false 参数非法或 Redis 异常
     */
    public boolean setBatch(Map<String, Object> map, Duration timeout) {
        if (map == null || map.isEmpty() || isInvalidTtl(timeout)) {
            return false;
        }
        try {
            RBatch batch = redissonClient.createBatch();
            map.forEach((key, value) -> {
                if (timeout != null) {
                    batch.getBucket(key).setAsync(value, timeout);
                } else {
                    batch.getBucket(key).setAsync(value);
                }
            });
            batch.execute();
            return true;
        } catch (Exception e) {
            log.error("Redis setBatch异常, timeout={}", timeout, e);
            return false;
        }
    }


    // ======================== List 操作（RList）=======================

    /**
     * 将 List 数据放入缓存（先清空再写入），过期时间取默认 TTL（见类注释）
     *
     * <p> 全量覆盖语义，其余约束见 {@link #setList(String, List, Duration)}
     *
     * @param key      键，为空直接返回 false
     * @param dataList 数据列表，为空或 null 时按清空处理
     * @return true 写入成功；false 参数非法或 Redis 异常
     */
    public <T> boolean setList(String key, List<T> dataList) {
        return setList(key, dataList, defaultTtl);
    }

    /**
     * 将 List 数据放入缓存（先清空再写入）
     *
     * <p> clear + addAll 非原子，并发重建同一 key 时可能短暂读到混合数据（重建型缓存可接受）
     * dataList 为空时仍会清空 key 并设过期时间，结果是缓存了一个空列表，而不是删掉 key
     *
     * @param key      键，为空直接返回 false
     * @param dataList 数据列表，为空或 null 时按清空处理
     * @param timeout  过期时间，必须大于 0；传 {@code null} 表示永久存储
     * @return true 写入成功；false 参数非法或 Redis 异常
     */
    public <T> boolean setList(String key, List<T> dataList, Duration timeout) {
        if (!StringUtils.hasText(key) || isInvalidTtl(timeout)) {
            return false;
        }
        try {
            RList<T> list = redissonClient.getList(key);
            list.clear();
            if (dataList != null && !dataList.isEmpty()) {
                list.addAll(dataList);
            }
            if (timeout != null) {
                list.expire(timeout);
            }
            return true;
        } catch (Exception e) {
            log.error("Redis setList异常, key={}, timeout={}", key, timeout, e);
            return false;
        }
    }

    /**
     * 获取 List 缓存
     *
     * <p> 返回 {@code ArrayList} 内存副本而非 RList 远程代理，改动不回写 Redis；
     * 元素整体拉回内存，大 List 需留意占用
     *
     * @param key 键，为空返回空集合
     * @return 缓存的列表；key 为空、不存在或 Redis 异常返回空集合
     */
    public <T> List<T> getList(String key) {
        if (!StringUtils.hasText(key)) {
            return Collections.emptyList();
        }
        try {
            RList<T> list = redissonClient.getList(key);
            return new ArrayList<>(list);
        } catch (Exception e) {
            log.error("Redis getList异常, key={}", key, e);
            return Collections.emptyList();
        }
    }

    /**
     * 向 List 尾部追加一个元素（相当于 RPUSH），过期时间取默认 TTL（见类注释）
     *
     * @param key   键，为空直接返回 false
     * @param value 元素值，需可被 Jackson 序列化；为空直接返回 false
     * @return true 追加成功；false 参数非法或 Redis 异常
     */
    public <T> boolean listAdd(String key, T value) {
        return listAdd(key, value, defaultTtl);
    }

    /**
     * 向 List 尾部追加一个元素（相当于 RPUSH）
     *
     * <p> 与 {@link #setList(String, List, Duration)} 的全量覆盖互补，用于追加型 / 队列型场景
     * 追加后若 {@code timeout} 非空会刷新整个 List 的过期时间（活跃续期）
     *
     * @param key     键，为空直接返回 false
     * @param value   元素值，需可被 Jackson 序列化；为空直接返回 false
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储
     * @return true 追加成功；false 参数非法或 Redis 异常
     */
    public <T> boolean listAdd(String key, T value, Duration timeout) {
        if (!StringUtils.hasText(key) || value == null || isInvalidTtl(timeout)) {
            return false;
        }
        try {
            RList<T> list = redissonClient.getList(key);
            list.add(value);
            if (timeout != null) {
                list.expire(timeout);
            }
            return true;
        } catch (Exception e) {
            log.error("Redis listAdd异常, key={}, timeout={}", key, timeout, e);
            return false;
        }
    }


    // ======================== Set 操作（RSet）=========================

    /**
     * 将 Set 数据放入缓存（先清空再写入），过期时间取默认 TTL（见类注释）
     *
     * @param key     键，为空直接返回 false
     * @param dataSet 数据集合，为空或 null 时按清空处理
     * @return true 写入成功；false 参数非法或 Redis 异常
     */
    public <T> boolean setSet(String key, Set<T> dataSet) {
        return setSet(key, dataSet, defaultTtl);
    }

    /**
     * 将 Set 数据放入缓存（先清空再写入）
     *
     * <p> clear + addAll 非原子，并发重建同一 key 时可能短暂读到混合数据（重建型缓存可接受）
     *
     * @param key     键，为空直接返回 false
     * @param dataSet 数据集合，为空或 null 时按清空处理
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储
     * @return true 写入成功；false 参数非法或 Redis 异常
     */
    public <T> boolean setSet(String key, Set<T> dataSet, Duration timeout) {
        if (!StringUtils.hasText(key) || isInvalidTtl(timeout)) {
            return false;
        }
        try {
            RSet<T> set = redissonClient.getSet(key);
            set.clear();
            if (dataSet != null && !dataSet.isEmpty()) {
                set.addAll(dataSet);
            }
            if (timeout != null) {
                set.expire(timeout);
            }
            return true;
        } catch (Exception e) {
            log.error("Redis setSet异常, key={}, timeout={}", key, timeout, e);
            return false;
        }
    }

    /**
     * 获取 Set 缓存
     *
     * <p> 返回 {@code HashSet} 内存副本而非 RSet 远程代理，改动不回写 Redis；
     * 元素整体拉回内存，大 Set 需留意占用
     *
     * @param key 键，为空返回空集合
     * @return 缓存的集合；key 为空、不存在或 Redis 异常返回空集合
     */
    public <T> Set<T> getSet(String key) {
        if (!StringUtils.hasText(key)) {
            return Collections.emptySet();
        }
        try {
            RSet<T> set = redissonClient.getSet(key);
            return new HashSet<>(set);
        } catch (Exception e) {
            log.error("Redis getSet异常, key={}", key, e);
            return Collections.emptySet();
        }
    }

    /**
     * 向 Set 添加一个元素，过期时间取默认 TTL（见类注释）
     *
     * <p> 元素天然去重（已存在则无操作），其余约束见 {@link #addSetValue(String, Object, Duration)}
     *
     * @param key   键，为空直接返回 false
     * @param value 元素值，需可被 Jackson 序列化；为空直接返回 false
     * @return true 添加成功；false 参数非法或 Redis 异常
     */
    public <T> boolean addSetValue(String key, T value) {
        return addSetValue(key, value, defaultTtl);
    }

    /**
     * 向 Set 添加一个元素（去重集合增量写入）
     *
     * <p> 元素天然去重（已存在则无操作）；追加后若 {@code timeout} 非空会刷新整个 Set 的过期时间，
     * 永久存储的 Set 走无 TTL 重载会被降级为默认 TTL（见类注释）
     *
     * @param key     键，为空直接返回 false
     * @param value   元素值，需可被 Jackson 序列化；为空直接返回 false
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储
     * @return true 添加成功；false 参数非法或 Redis 异常
     */
    public <T> boolean addSetValue(String key, T value, Duration timeout) {
        if (!StringUtils.hasText(key) || value == null || isInvalidTtl(timeout)) {
            return false;
        }
        try {
            RSet<T> set = redissonClient.getSet(key);
            set.add(value);
            if (timeout != null) {
                set.expire(timeout);
            }
            return true;
        } catch (Exception e) {
            log.error("Redis addSetValue异常, key={}, timeout={}", key, timeout, e);
            return false;
        }
    }

    /**
     * 从 Set 移除一个元素，过期时间取默认 TTL（见类注释）
     *
     * <p> 元素原本不存在也返回 true，其余约束见 {@link #removeSetValue(String, Object, Duration)}
     *
     * @param key   键，为空直接返回 false
     * @param value 元素值，为空直接返回 false
     * @return true 执行成功；false 参数非法或 Redis 异常
     */
    public <T> boolean removeSetValue(String key, T value) {
        return removeSetValue(key, value, defaultTtl);
    }

    /**
     * 从 Set 移除一个元素
     *
     * <p> 移除后若 {@code timeout} 非空会刷新整个 Set 的过期时间：
     * 对永久存储的 Set 用无 TTL 重载，会把它降级为默认 TTL（见类注释）
     * 元素原本不存在也返回 true，需要区分时先 {@link #isSetMember(String, Object)}
     *
     * @param key     键，为空直接返回 false
     * @param value   元素值，为空直接返回 false
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储
     * @return true 执行成功；false 参数非法或 Redis 异常
     */
    public <T> boolean removeSetValue(String key, T value, Duration timeout) {
        if (!StringUtils.hasText(key) || value == null || isInvalidTtl(timeout)) {
            return false;
        }
        try {
            RSet<T> set = redissonClient.getSet(key);
            set.remove(value);
            if (timeout != null) {
                set.expire(timeout);
            }
            return true;
        } catch (Exception e) {
            log.error("Redis removeSetValue异常, key={}, timeout={}", key, timeout, e);
            return false;
        }
    }

    /**
     * 判断 Set 中是否包含指定元素（成员判断，O(1)）
     *
     * @param key   键，为空返回 false
     * @param value 元素值，为空返回 false
     * @return true 存在；false 不存在、参数非法或 Redis 异常
     */
    public <T> boolean isSetMember(String key, T value) {
        if (!StringUtils.hasText(key) || value == null) {
            return false;
        }
        try {
            return redissonClient.<T>getSet(key).contains(value);
        } catch (Exception e) {
            log.error("Redis isSetMember异常, key={}", key, e);
            return false;
        }
    }


    // ======================== Map 操作（RMap）=========================

    /**
     * 将 Map 数据放入缓存（先清空再写入），过期时间取默认 TTL（见类注释）
     *
     * <p> 全量覆盖语义，其余约束见 {@link #setMap(String, Map, Duration)}
     *
     * @param key 键，为空直接返回 false
     * @param map 数据映射，为空或 null 时按清空处理
     * @return true 写入成功；false 参数非法或 Redis 异常
     */
    public <K, V> boolean setMap(String key, Map<K, V> map) {
        return setMap(key, map, defaultTtl);
    }

    /**
     * 将 Map 数据放入缓存（先清空再写入）
     *
     * <p> 适用于重建型缓存（整体覆盖、无历史残留）；若需增量更新单个字段，
     * 使用 {@link #setMapValue(String, Object, Object, Duration)}
     * clear + putAll 非原子，并发重建同一 key 时可能短暂读到混合数据
     *
     * @param key     键，为空直接返回 false
     * @param map     数据映射，为空或 null 时按清空处理
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储
     * @return true 写入成功；false 参数非法或 Redis 异常
     */
    public <K, V> boolean setMap(String key, Map<K, V> map, Duration timeout) {
        if (!StringUtils.hasText(key) || isInvalidTtl(timeout)) {
            return false;
        }
        try {
            RMap<K, V> rMap = redissonClient.getMap(key);
            rMap.clear();
            if (map != null && !map.isEmpty()) {
                rMap.putAll(map);
            }
            if (timeout != null) {
                rMap.expire(timeout);
            }
            return true;
        } catch (Exception e) {
            log.error("Redis setMap异常, key={}, timeout={}", key, timeout, e);
            return false;
        }
    }

    /**
     * 设置 Map 中指定 hashKey 的值，过期时间取默认 TTL（见类注释）
     *
     * @param key     键，为空直接返回 false
     * @param hashKey Map 内部的键，为空直接返回 false
     * @param value   值，需可被 Jackson 序列化
     * @return true 写入成功；false 参数非法或 Redis 异常
     */
    public <K, V> boolean setMapValue(String key, K hashKey, V value) {
        return setMapValue(key, hashKey, value, defaultTtl);
    }

    /**
     * 设置 Map 中指定 hashKey 的值
     *
     * <p> Redis Hash 的过期时间只能作用于整个 key，不能作用于单个 hashKey：
     * {@code timeout} 非空时会刷新整个 Map 的过期时间，若该 Map 此前是永久存储
     * （{@code setMap(key, map, null)}）会被静默降级为默认 TTL，导致重建型缓存提前过期，
     * 这类场景请显式传 {@code null}
     *
     * @param key     键，为空直接返回 false
     * @param hashKey Map 内部的键，为空直接返回 false
     * @param value   值，需可被 Jackson 序列化
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储
     * @return true 写入成功；false 参数非法或 Redis 异常
     */
    public <K, V> boolean setMapValue(String key, K hashKey, V value, Duration timeout) {
        if (!StringUtils.hasText(key) || hashKey == null || isInvalidTtl(timeout)) {
            return false;
        }
        try {
            RMap<K, V> rMap = redissonClient.getMap(key);
            rMap.put(hashKey, value);
            if (timeout != null) {
                rMap.expire(timeout);
            }
            return true;
        } catch (Exception e) {
            log.error("Redis setMapValue异常, key={}, hashKey={}, timeout={}", key, hashKey, timeout, e);
            return false;
        }
    }

    /**
     * 获取整个 Map 缓存
     *
     * <p> 返回 {@code HashMap} 内存副本而非 RMap 远程代理，改动不回写 Redis；
     * 字段整体拉回内存，只要部分字段时用 {@link #batchGetMapValues(String, Collection)} 更省
     *
     * @param key 键，为空返回空 Map
     * @return 全部键值映射；key 为空、不存在或 Redis 异常返回空 Map
     */
    public <K, V> Map<K, V> getMap(String key) {
        if (!StringUtils.hasText(key)) {
            return Collections.emptyMap();
        }
        try {
            return new HashMap<>(redissonClient.<K, V>getMap(key).readAllMap());
        } catch (Exception e) {
            log.error("Redis getMap异常, key={}", key, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 获取 Map 中指定 hashKey 的值
     *
     * @param key     键，为空返回 null
     * @param hashKey Map 内部的键，为空返回 null
     * @return 值；key 为空、hashKey 不存在或 Redis 异常返回 null
     */
    public <K, V> V getMapValue(String key, K hashKey) {
        if (!StringUtils.hasText(key) || hashKey == null) {
            return null;
        }
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
     * @param key 键，为空返回空集合
     * @return hashKey 集合（内存副本）；key 为空、不存在或 Redis 异常返回空集合
     */
    public <K> Set<K> getMapKeys(String key) {
        if (!StringUtils.hasText(key)) {
            return Collections.emptySet();
        }
        try {
            return new HashSet<>(redissonClient.<K, Object>getMap(key).keySet());
        } catch (Exception e) {
            log.error("Redis getMapKeys异常, key={}", key, e);
            return Collections.emptySet();
        }
    }

    /**
     * 批量获取 Map 中指定 hashKey 子集的值（一次网络往返）
     *
     * <p> 与 {@link #getMap(String)} 取全部字段的用途区分：本方法只读入参指定的字段，
     * 大 Hash 按需取字段时可以少传很多数据
     * 返回的是 {@code Collection} 而非 {@code Map}，hashKey 与值的对应关系会丢失；
     * 未命中的 hashKey 不出现在结果里，顺序也不保证
     *
     * @param key      键，为空返回空集合
     * @param hashKeys Map 内部的键集合，为空返回空集合
     * @return 命中字段的值集合（内存副本）；key 为空、不存在或 Redis 异常返回空集合
     */
    public <K, V> Collection<V> batchGetMapValues(String key, Collection<K> hashKeys) {
        if (!StringUtils.hasText(key) || hashKeys == null || hashKeys.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            RMap<K, V> map = redissonClient.getMap(key);
            Map<K, V> result = map.getAll(new HashSet<>(hashKeys));
            return new ArrayList<>(result.values());
        } catch (Exception e) {
            log.error("Redis batchGetMapValues异常, key={}, hashKeys={}", key, hashKeys, e);
            return Collections.emptyList();
        }
    }

    /**
     * 从 Map 移除指定 hashKey
     *
     * @param key     键，为空返回 false
     * @param hashKey Map 内部的键，为空返回 false
     * @return true 确实移除了该 hashKey；false 原本不存在、参数非法或 Redis 异常
     */
    public <K> boolean removeMapValue(String key, K hashKey) {
        if (!StringUtils.hasText(key) || hashKey == null) {
            return false;
        }
        try {
            long removed = redissonClient.<K, Object>getMap(key).fastRemove(hashKey);
            return removed > 0;
        } catch (Exception e) {
            log.error("Redis removeMapValue异常, key={}, hashKey={}", key, hashKey, e);
            return false;
        }
    }

    /**
     * 判断 Map 中是否存在指定 hashKey
     *
     * @param key     键，为空返回 false
     * @param hashKey Map 内部的键，为空返回 false
     * @return true 存在；false 不存在、参数非法或 Redis 异常
     */
    public <K> boolean hasMapKey(String key, K hashKey) {
        if (!StringUtils.hasText(key) || hashKey == null) {
            return false;
        }
        try {
            return redissonClient.<K, Object>getMap(key).containsKey(hashKey);
        } catch (Exception e) {
            log.error("Redis hasMapKey异常, key={}, hashKey={}", key, hashKey, e);
            return false;
        }
    }


    // ======================== 原子计数器（RAtomicLong）================

    /**
     * 自增 1 并返回自增后的值，过期时间取默认 TTL（见类注释）
     *
     * <p> 自增后刷新过期时间，活跃计数器持续续期；长时间无自增则到期自动清理
     *
     * @param key 键，为空直接返回 0
     * @return 自增后的值；参数非法或 Redis 异常返回 0
     */
    public Long incrAndGet(String key) {
        return incrAndGet(key, defaultTtl);
    }

    /**
     * 自增 1 并返回自增后的值
     *
     * <p> 自增后若 {@code timeout} 非空则刷新过期时间（活跃续期），计数类数据建议都传，
     * 不传就是永久堆积
     *
     * <p> 失败与真实值 0 无法区分：参数非法、Redis 异常同样返回 0
     *
     * @param key     键，为空直接返回 0
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储
     * @return 自增后的值；参数非法或 Redis 异常返回 0
     */
    public Long incrAndGet(String key, Duration timeout) {
        if (!StringUtils.hasText(key) || isInvalidTtl(timeout)) {
            return 0L;
        }
        try {
            RAtomicLong counter = redissonClient.getAtomicLong(key);
            long result = counter.incrementAndGet();
            if (timeout != null) {
                counter.expire(timeout);
            }
            return result;
        } catch (Exception e) {
            log.error("Redis incrAndGet异常, key={}, timeout={}", key, timeout, e);
            return 0L;
        }
    }

    /**
     * 自减 1 并返回自减后的值，过期时间取默认 TTL（见类注释）
     *
     * @param key 键，为空直接返回 0
     * @return 自减后的值；参数非法或 Redis 异常返回 0
     */
    public Long decrAndGet(String key) {
        return decrAndGet(key, defaultTtl);
    }

    /**
     * 自减 1 并返回自减后的值
     *
     * @param key     键，为空直接返回 0
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储
     * @return 自减后的值；参数非法或 Redis 异常返回 0
     */
    public Long decrAndGet(String key, Duration timeout) {
        if (!StringUtils.hasText(key) || isInvalidTtl(timeout)) {
            return 0L;
        }
        try {
            RAtomicLong counter = redissonClient.getAtomicLong(key);
            long result = counter.decrementAndGet();
            if (timeout != null) {
                counter.expire(timeout);
            }
            return result;
        } catch (Exception e) {
            log.error("Redis decrAndGet异常, key={}, timeout={}", key, timeout, e);
            return 0L;
        }
    }

    /**
     * 增加指定增量并返回增加后的值（负数为自减），过期时间取默认 TTL（见类注释）
     *
     * @param key       键，为空直接返回 0
     * @param increment 增量，可正可负
     * @return 增加后的值；参数非法或 Redis 异常返回 0
     */
    public Long incrBy(String key, long increment) {
        return incrBy(key, increment, defaultTtl);
    }

    /**
     * 增加指定增量并返回增加后的值（负数则为自减）
     *
     * @param key       键，为空直接返回 0
     * @param increment 增量，可正可负
     * @param timeout   过期时间，必须大于 0；传 {@code null} 表示永久存储
     * @return 增加后的值；参数非法或 Redis 异常返回 0
     */
    public Long incrBy(String key, long increment, Duration timeout) {
        if (!StringUtils.hasText(key) || isInvalidTtl(timeout)) {
            return 0L;
        }
        try {
            RAtomicLong counter = redissonClient.getAtomicLong(key);
            long result = counter.addAndGet(increment);
            if (timeout != null) {
                counter.expire(timeout);
            }
            return result;
        } catch (Exception e) {
            log.error("Redis incrBy异常, key={}, increment={}, timeout={}", key, increment, timeout, e);
            return 0L;
        }
    }

    /**
     * 获取当前原子计数器的值
     *
     * @param key 键，为空返回 0
     * @return 当前值；key 为空、不存在或 Redis 异常返回 0
     */
    public Long getAtomicValue(String key) {
        if (!StringUtils.hasText(key)) {
            return 0L;
        }
        try {
            return redissonClient.getAtomicLong(key).get();
        } catch (Exception e) {
            log.error("Redis getAtomicValue异常, key={}", key, e);
            return 0L;
        }
    }


    // ======================== Key 操作 ================================

    /**
     * 删除单个 key
     *
     * @param key 键，为空返回 false
     * @return true 确实删掉了 key；false key 不存在、参数非法或 Redis 异常
     */
    public boolean delete(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        try {
            return redissonClient.getBucket(key).delete();
        } catch (Exception e) {
            log.error("Redis delete异常, key={}", key, e);
            return false;
        }
    }

    /**
     * 批量删除 key
     *
     * @param keys 键集合，为空返回 false
     * @return true 命令执行成功（不保证删掉了几个）；false 参数非法或 Redis 异常
     */
    public boolean delete(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return false;
        }
        try {
            redissonClient.getKeys().delete(keys.toArray(new String[0]));
            return true;
        } catch (Exception e) {
            log.error("Redis批量delete异常, keys={}", keys, e);
            return false;
        }
    }

    /**
     * 判断 key 是否存在
     *
     * @param key 键，为空返回 false
     * @return true 存在；false 不存在、key 为空或 Redis 异常
     */
    public boolean hasKey(String key) {
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
     * <p> key 不存在时返回 false，不会凭空把 key 建出来
     *
     * @param key     键，为空返回 false
     * @param timeout 过期时间，必须大于 0
     * @return true 设置成功；false key 不存在、参数非法或 Redis 异常
     */
    public boolean expire(String key, Duration timeout) {
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
     * @param key     键，为空返回 false
     * @param timeout 过期时长，必须大于 0
     * @param unit    timeout 的单位，为空抛 NullPointerException
     * @return 同 {@link #expire(String, Duration)}
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return expire(key, Duration.of(timeout, unit.toChronoUnit()));
    }

    /**
     * 设置过期时间点
     *
     * <p> key 不存在时返回 false，不会凭空把 key 建出来
     *
     * @param key  键，为空返回 false
     * @param date 失效时间点，为空返回 false
     * @return true 设置成功；false key 不存在、参数非法或 Redis 异常
     */
    public boolean expireAt(String key, Date date) {
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
     * 返回 key 的剩余过期时间，单位秒
     *
     * <p> 单位换算见 {@link #getExpire(String, TimeUnit)}
     *
     * @param key 键，为空返回 0
     * @return 剩余秒数；-1 永不过期、-2 key 不存在（Redis 协议约定），参数非法或异常返回 0
     */
    public Long getExpire(String key) {
        return getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 返回 key 的剩余过期时间
     *
     * <p> 底层精度是毫秒，换算到其他单位时截断取整：剩余 59.9 秒按秒返回是 59
     *
     * @param key  键，为空返回 0
     * @param unit 时间单位，为空返回 0
     * @return 剩余时间；-1 永不过期、-2 key 不存在（Redis 协议约定），参数非法或异常返回 0
     */
    public Long getExpire(String key, TimeUnit unit) {
        if (!StringUtils.hasText(key) || unit == null) {
            return 0L;
        }
        try {
            long millis = redissonClient.getBucket(key).remainTimeToLive();
            // remainTimeToLive() 返回毫秒：-1 永不过期、-2 key 不存在，负值无需换算单位，原样返回
            if (millis < 0) {
                return millis;
            }
            return unit.convert(millis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Redis getExpire异常, key={}", key, e);
            return 0L;
        }
    }


    // ======================== 分布式锁（RLock）=======================

    /**
     * 在分布式锁内执行业务动作，持锁时长启用看门狗自动续期
     *
     * <p> 统一封装「tryLock + finally 解锁」样板，锁 key 自动加 {@code yeed:lock:} 前缀：
     * 拿不到锁返回 null 而不抛异常（并发没抢到是常见路径），
     * 业务动作自身抛出的异常照常向上传播，锁在 finally 中释放
     *
     * <p> 要固定持锁时长（放弃看门狗）用
     * {@link #executeWithLock(String, Duration, Duration, Supplier)}
     *
     * @param lockKey  锁的业务标识（如 {@code order:123:pay}），不能为空
     * @param waitTime 等待锁的最长时间，不能为空、不能为负；{@code Duration.ZERO} 只尝试一次
     * @param action   锁内执行的动作，不能为空
     * @param <T>      动作返回值类型
     * @return action 的执行结果；等待超时未拿到锁返回 null
     * @throws IllegalArgumentException lockKey / waitTime 非法时 fail-fast，锁场景不容忍静默无锁执行
     * @throws NullPointerException     action 为 null 时抛出
     */
    public <T> T executeWithLock(String lockKey, Duration waitTime, Supplier<T> action) {
        return executeWithLock(lockKey, waitTime, null, action);
    }

    /**
     * 在分布式锁内执行业务动作（获取锁失败返回 null 而非抛异常）
     *
     * <p> 统一封装「tryLock + finally 解锁」样板，锁 key 自动加 {@code yeed:lock:} 前缀：
     * 业务动作自身抛出的异常照常向上传播，锁在 finally 中释放
     *
     * @param lockKey   锁的业务标识（如 {@code order:123:pay}），不能为空
     * @param waitTime  等待锁的最长时间，不能为空、不能为负；{@code Duration.ZERO} 只尝试一次
     * @param leaseTime 锁的持有时间；传 {@code null} 启用 Redisson 看门狗自动续期
     *                  （进程崩溃也能自动释放，无死锁风险），传非空则到期强制释放，
     *                  业务没在期限内跑完会被提前解锁
     * @param action    锁内执行的动作，不能为空
     * @param <T>       动作返回值类型
     * @return action 的执行结果；等待超时未拿到锁返回 null
     * @throws IllegalArgumentException lockKey / waitTime / leaseTime 非法时 fail-fast，
     *                                 锁场景不容忍静默无锁执行
     * @throws NullPointerException     action 为 null 时抛出
     */
    public <T> T executeWithLock(String lockKey, Duration waitTime, Duration leaseTime, Supplier<T> action) {
        assertLockArgs(lockKey, waitTime, leaseTime);
        Objects.requireNonNull(action, "分布式锁 action 不能为空");
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + lockKey);
        boolean locked = false;
        try {
            locked = tryAcquire(lock, waitTime, leaseTime);
            if (!locked) {
                log.warn("获取分布式锁超时, lockKey={}, waitTime={}", lockKey, waitTime);
                return null;
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取分布式锁被中断, lockKey={}", lockKey, e);
            return null;
        } finally {
            if (locked) {
                releaseQuietly(lock, lockKey);
            }
        }
    }

    /**
     * 尝试获取分布式锁，持锁时长启用看门狗自动续期
     *
     * <p> 拿到锁后必须在业务结束时调用 {@link #unlock(String)}，漏掉要等进程崩溃才释放；
     * 更推荐 {@link #executeWithLock(String, Duration, Supplier)} 自动管理释放
     *
     * @param lockKey  锁的业务标识，不能为空
     * @param waitTime 等待锁的最长时间，不能为空、不能为负；{@code Duration.ZERO} 只尝试一次
     * @return true 拿到锁；false 等待超时未拿到、线程被中断或 Redis 异常
     * @throws IllegalArgumentException lockKey / waitTime 非法时 fail-fast
     */
    public boolean tryLock(String lockKey, Duration waitTime) {
        return tryLock(lockKey, waitTime, null);
    }

    /**
     * 尝试获取分布式锁
     *
     * <p> 拿到锁后必须在业务结束时调用 {@link #unlock(String)}，漏掉要等进程崩溃才释放；
     * 更推荐 {@link #executeWithLock(String, Duration, Duration, Supplier)} 自动管理释放
     *
     * @param lockKey   锁的业务标识，不能为空
     * @param waitTime  等待锁的最长时间，不能为空、不能为负；{@code Duration.ZERO} 只尝试一次
     * @param leaseTime 锁的持有时间；传 {@code null} 启用 Redisson 看门狗自动续期，
     *                  传非空则到期强制释放（业务没跑完也会被解锁）
     * @return true 拿到锁；false 等待超时未拿到、线程被中断或 Redis 异常
     * @throws IllegalArgumentException lockKey / waitTime / leaseTime 非法时 fail-fast
     */
    public boolean tryLock(String lockKey, Duration waitTime, Duration leaseTime) {
        assertLockArgs(lockKey, waitTime, leaseTime);
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + lockKey);
        try {
            return tryAcquire(lock, waitTime, leaseTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取分布式锁被中断, lockKey={}", lockKey, e);
            return false;
        } catch (Exception e) {
            log.error("获取分布式锁异常, lockKey={}", lockKey, e);
            return false;
        }
    }

    /**
     * 释放当前线程持有的分布式锁
     *
     * <p> 只释放当前线程持有的锁：别的线程持有或压根没锁时返回 false，不会误删他人锁
     *
     * @param lockKey 锁的业务标识，不能为空
     * @return true 释放成功；false 当前线程未持有该锁、参数非法或 Redis 异常
     */
    public boolean unlock(String lockKey) {
        if (!StringUtils.hasText(lockKey)) {
            return false;
        }
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + lockKey);
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("释放分布式锁异常, lockKey={}", lockKey, e);
            return false;
        }
    }


    // ======================== 发布订阅（RTopic，发布端）===============

    /**
     * 向指定主题发布消息（只封装发布端，订阅端由业务直接用 RTopic 管理监听器生命周期）
     *
     * <p> Redis 发布订阅是即发即忘：没有订阅者时消息直接丢弃，要求可靠投递的场景别用它
     *
     * @param topic   主题名，不能为空
     * @param message 消息体，需可被 Jackson 序列化，不能为空
     * @return true 发布成功；false 参数非法或 Redis 异常
     */
    public boolean publish(String topic, Object message) {
        if (!StringUtils.hasText(topic) || message == null) {
            return false;
        }
        try {
            redissonClient.getTopic(topic).publish(message);
            return true;
        } catch (Exception e) {
            log.error("Redis publish异常, topic={}", topic, e);
            return false;
        }
    }


    // ======================== 私有辅助方法 ============================

    /**
     * 校验分布式锁参数，非法即抛异常（fail-fast，避免静默无锁执行）
     */
    private static void assertLockArgs(String lockKey, Duration waitTime, Duration leaseTime) {
        if (!StringUtils.hasText(lockKey)) {
            throw new IllegalArgumentException("分布式锁 lockKey 不能为空");
        }
        if (waitTime == null || waitTime.isNegative()) {
            throw new IllegalArgumentException("分布式锁 waitTime 必须大于等于 0");
        }
        if (leaseTime != null && (leaseTime.isNegative() || leaseTime.isZero())) {
            throw new IllegalArgumentException("分布式锁 leaseTime 必须大于 0");
        }
    }

    /**
     * 尝试获取锁：leaseTime 非空则指定持有时间（关闭看门狗续期），否则启用看门狗自动续期
     */
    private static boolean tryAcquire(RLock lock, Duration waitTime, Duration leaseTime) throws InterruptedException {
        if (leaseTime != null) {
            return lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
        }
        return lock.tryLock(waitTime.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * 安静释放锁：仅当前线程持有锁时解锁，异常只记日志（finally 中不可再抛异常）
     */
    private static void releaseQuietly(RLock lock, String lockKey) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            log.error("释放分布式锁异常, lockKey={}", lockKey, e);
        }
    }

}
