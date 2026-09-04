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
 * <p>统一做参数校验与异常捕获：写操作参数非法时直接忽略并返回 {@code false}，
 * 读操作参数非法或 Redis 异常时记录日志并返回安全默认值（null / 空集合 / 0 / false），
 * 避免 Redis 抖动直接中断业务流程；写方法返回 {@code boolean} 表示操作是否成功，
 * 调用方可据此感知写入失败（如缓存与库不一致排查）。
 *
 * <p>分布式锁与发布订阅提供薄封装：锁模板
 * {@link #executeWithLock(String, Duration, Supplier)} 统一「tryLock + finally 解锁」样板，
 * 锁 key 统一加 {@code yeed:lock:} 前缀（遵守
 * {@link com.yeungzhy.yeed.common.core.constant.CacheConstant} 命名规范）；
 * 锁方法参数非法时 fail-fast 抛 {@link IllegalArgumentException}，避免静默无锁执行。
 * 限流（RRateLimiter）、订阅（RTopic 监听器生命周期）等其余能力不在此封装，
 * 业务代码直接注入 {@link RedissonClient} 使用。
 *
 * <p>通过 {@link com.yeungzhy.yeed.common.cache.config.RedisAutoConfiguration} 注册为 Spring Bean
 *
 * <p>过期时间（TTL）策略：
 * <ul>
 *   <li>写方法不显式传 {@code timeout} 时，使用默认过期时间
 *   （配置项 {@code yeed.cache.default-ttl}，见
 *   {@link com.yeungzhy.yeed.common.cache.config.CacheProperties}，默认 24 小时），
 *   防止数据永久堆积；</li>
 *   <li>显式传 {@code timeout} 为 {@code null} 表示永久存储（不设置过期时间），
 *   仅适用于业务主动管理生命周期、每次整体重建覆盖的重建型缓存（如菜单接口权限缓存），
 *   调用方需在注释中说明永久存储的理由；</li>
 *   <li>非法 timeout（负数 / 零）按参数非法忽略，不执行写操作。</li>
 * </ul>
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
     * timeout 是否非法：null 合法（表示永久存储），负数/零非法
     */
    private static boolean isInvalidTtl(Duration timeout) {
        return timeout != null && (timeout.isNegative() || timeout.isZero());
    }


    /* ==================== 对象存取（RBucket）===================== */

    /**
     * 设置指定 key 的值（使用默认过期时间，见类注释 TTL 策略）
     *
     * @param key   键，不能为空
     * @param value 值
     * @return true 写入成功；false 参数非法或 Redis 异常
     */
    public boolean set(String key, Object value) {
        return set(key, value, defaultTtl);
    }

    /**
     * 设置指定 key 的值和过期时间
     *
     * @param key     键，不能为空
     * @param value   值
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储（不设置过期时间）
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
     * @param key     键，不能为空
     * @param value   值
     * @param timeout 过期时间，必须大于 0
     * @param unit    时间单位，不能为空
     * @return true 写入成功；false 参数非法或 Redis 异常
     */
    public boolean set(String key, Object value, long timeout, TimeUnit unit) {
        return set(key, value, Duration.of(timeout, unit.toChronoUnit()));
    }

    /**
     * 获取指定 key 的值
     *
     * @param key 键
     * @return 值；key为空、不存在或异常返回 null
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
     * 只有在 key 不存在时设置 key 的值（使用默认过期时间，见类注释 TTL 策略）
     *
     * @param key   键，不能为空
     * @param value 值
     * @return true 设置成功；false key已存在或异常
     */
    public boolean setIfAbsent(String key, Object value) {
        return setIfAbsent(key, value, defaultTtl);
    }

    /**
     * 只有在 key 不存在时设置 key 的值和过期时间
     *
     * @param key     键，不能为空
     * @param value   值
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储（不设置过期时间）
     * @return true 设置成功；false key已存在或异常
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
     * @param key     键，不能为空
     * @param value   值
     * @param timeout 过期时间，必须大于 0
     * @param unit    时间单位，不能为空
     * @return true 设置成功；false key已存在或异常
     */
    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        return setIfAbsent(key, value, Duration.of(timeout, unit.toChronoUnit()));
    }

    /**
     * 获取并删除指定 key 的值（一次性消费场景：防重令牌、一次性凭证等）
     *
     * @param key 键
     * @return 删除前的值；key为空、不存在或异常返回 null
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


    /* ==================== 批量操作（RBatch / RBuckets）============= */

    /**
     * 批量获取多个 key 的值（一次网络往返）
     *
     * @param keys 键集合，不能为空
     * @return 键值映射（{@code Map<K, V>}）；异常或参数非法返回空集合
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
     * 批量设置多个 key 的值（一次网络往返，使用默认过期时间，见类注释 TTL 策略）
     *
     * @param map 键值对集合，不能为空
     * @return true 写入成功；false 参数非法或 Redis 异常
     */
    public boolean setBatch(Map<String, Object> map) {
        return setBatch(map, defaultTtl);
    }

    /**
     * 批量设置多个 key 的值和过期时间
     *
     * <p>通过 RBatch 一次网络往返提交，且每条命令自带 TTL（无需写入后再逐 key expire），
     * 避免了逐 key 设置过期时间产生的「部分 key 有 TTL、部分没有」的脏状态；
     * 批量整体执行非原子（中途失败可能出现部分写入），业务需可容忍
     *
     * @param map     键值对集合，不能为空
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储（不设置过期时间）
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


    /* ==================== List 操作（RList）===================== */

    /**
     * 将 List 数据放入缓存（先清空再写入，使用默认过期时间，见类注释 TTL 策略）
     *
     * @param key      键，不能为空
     * @param dataList 数据列表
     * @return true 写入成功；false 参数非法或 Redis 异常
     */
    public <T> boolean setList(String key, List<T> dataList) {
        return setList(key, dataList, defaultTtl);
    }

    /**
     * 将 List 数据放入缓存（先清空再写入）
     *
     * <p>clear + addAll 非原子，并发重建同一 key 时可能短暂读到混合数据（重建型缓存可接受）
     *
     * @param key      键，不能为空
     * @param dataList 数据列表
     * @param timeout  过期时间，必须大于 0；传 {@code null} 表示永久存储（不设置过期时间）
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
     * 获取 List 缓存（返回内存副本）
     *
     * @param key 键
     * @return List；key为空、异常或不存在返回空集合
     *
     * <p>返回的是 {@code ArrayList} 副本而非 RList 远程代理，
     * 直接修改返回结果不会影响 Redis 中的数据
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
     * 向 List 尾部追加一个元素（相当于 RPUSH，使用默认过期时间，见类注释 TTL 策略）
     *
     * @param key   键，不能为空
     * @param value 元素值，不能为空
     * @return true 追加成功；false 参数非法或 Redis 异常
     */
    public <T> boolean listAdd(String key, T value) {
        return listAdd(key, value, defaultTtl);
    }

    /**
     * 向 List 尾部追加一个元素（相当于 RPUSH）
     *
     * <p>追加后若 {@code timeout} 非空会刷新整个 List 的过期时间（活跃续期）；
     * 与 {@link #setList(String, List)} 的全量覆盖互补，用于追加型/队列型场景
     *
     * @param key     键，不能为空
     * @param value   元素值，不能为空
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储（不设置过期时间）
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


    /* ==================== Set 操作（RSet）======================= */

    /**
     * 将 Set 数据放入缓存（先清空再写入，使用默认过期时间，见类注释 TTL 策略）
     *
     * @param key     键，不能为空
     * @param dataSet 数据集合
     * @return true 写入成功；false 参数非法或 Redis 异常
     */
    public <T> boolean setSet(String key, Set<T> dataSet) {
        return setSet(key, dataSet, defaultTtl);
    }

    /**
     * 将 Set 数据放入缓存（先清空再写入）
     *
     * @param key     键，不能为空
     * @param dataSet 数据集合
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储（不设置过期时间）
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
     * 获取 Set 缓存（返回内存副本）
     *
     * @param key 键
     * @return Set；key为空、异常或不存在返回空集合
     *
     * <p>返回的是 {@code HashSet} 副本而非 RSet 远程代理，
     * 直接修改返回结果不会影响 Redis 中的数据
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
     * 向 Set 添加一个元素（去重集合增量写入，使用默认过期时间，见类注释 TTL 策略）
     *
     * @param key   键，不能为空
     * @param value 元素值，不能为空
     * @return true 添加成功；false 参数非法或 Redis 异常
     */
    public <T> boolean addSetValue(String key, T value) {
        return addSetValue(key, value, defaultTtl);
    }

    /**
     * 向 Set 添加一个元素（去重集合增量写入）
     *
     * <p>元素天然去重（已存在则无操作）；追加后若 {@code timeout} 非空会刷新整个 Set 的过期时间
     *
     * @param key     键，不能为空
     * @param value   元素值，不能为空
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储（不设置过期时间）
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
     * 从 Set 移除一个元素（使用默认过期时间，见类注释 TTL 策略）
     *
     * @param key   键，不能为空
     * @param value 元素值，不能为空
     * @return true 移除成功；false 参数非法或 Redis 异常
     */
    public <T> boolean removeSetValue(String key, T value) {
        return removeSetValue(key, value, defaultTtl);
    }

    /**
     * 从 Set 移除一个元素
     *
     * @param key     键，不能为空
     * @param value   元素值，不能为空
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储（不设置过期时间）
     * @return true 移除成功；false 参数非法或 Redis 异常
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
     * @param key   键
     * @param value 元素值
     * @return true 存在；false 不存在、参数非法或异常
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


    /* ==================== Map 操作（RMap）======================= */

    /**
     * 将 Map 数据放入缓存（先清空再写入，使用默认过期时间，见类注释 TTL 策略）
     *
     * @param key 键，不能为空
     * @param map 数据映射
     * @return true 写入成功；false 参数非法或 Redis 异常
     */
    public <K, V> boolean setMap(String key, Map<K, V> map) {
        return setMap(key, map, defaultTtl);
    }

    /**
     * 将 Map 数据放入缓存（先清空再写入）
     *
     * <p>适用于重建型缓存（整体覆盖、无历史残留）；若需增量更新单个字段，
     * 使用 {@link #setMapValue(String, Object, Object, Duration)}
     *
     * @param key     键，不能为空
     * @param map     数据映射
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储（不设置过期时间）
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
     * 设置 Map 中指定 hashKey 的值（使用默认过期时间，见类注释 TTL 策略）
     *
     * @param key     键，不能为空
     * @param hashKey Map 内部的键，不能为空
     * @param value   值
     * @return true 写入成功；false 参数非法或 Redis 异常
     */
    public <K, V> boolean setMapValue(String key, K hashKey, V value) {
        return setMapValue(key, hashKey, value, defaultTtl);
    }

    /**
     * 设置 Map 中指定 hashKey 的值
     *
     * <p>高危注意：Redis Hash 的过期时间作用于整个 key而非单个 hashKey。
     * 本方法在 {@code timeout} 非空时会刷新整个 Map 的过期时间——若该 Map
     * 此前是永久存储（{@code setMap(key, map, null)}），此处会被静默降级为默认 TTL，
     * 导致重建型缓存提前过期。重建型缓存请使用 {@link #setMap(String, Map, Duration)}
     *
     * @param key     键，不能为空
     * @param hashKey Map 内部的键，不能为空
     * @param value   值
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储（不设置过期时间）
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
     * 获取整个 Map 缓存（返回内存副本）
     *
     * @param key 键
     * @return 全部键值映射；key为空、异常或不存在返回空集合
     *
     * <p>返回的是 {@code HashMap} 副本而非 RMap 远程代理，
     * 直接修改返回结果不会影响 Redis 中的数据
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
     * @param key     键
     * @param hashKey Map 内部的键
     * @return 值；key为空、不存在或异常返回 null
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
     * @param key 键
     * @return hashKey 集合（内存副本）；key为空、异常或不存在返回空集合
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
     * <p>与 {@link #getMap(String)}（取全部字段）区分：本方法仅取入参指定的字段子集，
     * 常用于从同一 Hash 中按需读取多个字段
     *
     * @param key      键
     * @param hashKeys Map 内部的键集合
     * @return 命中字段的值集合（内存副本）；key为空、异常或不存在返回空集合
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
     * @param key     键，不能为空
     * @param hashKey Map 内部的键，不能为空
     * @return true 移除成功（hashKey 原本存在）；false 不存在、参数非法或异常
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
     * @param key     键
     * @param hashKey Map 内部的键
     * @return true 存在；false 不存在、参数非法或异常
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


    /* ==================== 原子计数器（RAtomicLong）============== */

    /**
     * 自增 1 并返回自增后的值（使用默认过期时间，见类注释 TTL 策略）
     *
     * <p>自增后刷新过期时间，活跃计数器持续续期；长时间无自增则到期自动清理
     *
     * @param key 键，不能为空
     * @return 自增后的值；异常返回 0
     */
    public Long incrAndGet(String key) {
        return incrAndGet(key, defaultTtl);
    }

    /**
     * 自增 1 并返回自增后的值
     *
     * <p>自增后若 {@code timeout} 非空则刷新过期时间（活跃续期）；
     * 计数类数据务必显式指定过期时间，避免永久堆积
     *
     * @param key     键，不能为空
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储（不设置过期时间）
     * @return 自增后的值；异常返回 0
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
     * 自减 1 并返回自减后的值（使用默认过期时间，见类注释 TTL 策略）
     *
     * @param key 键，不能为空
     * @return 自减后的值；异常返回 0
     */
    public Long decrAndGet(String key) {
        return decrAndGet(key, defaultTtl);
    }

    /**
     * 自减 1 并返回自减后的值
     *
     * @param key     键，不能为空
     * @param timeout 过期时间，必须大于 0；传 {@code null} 表示永久存储（不设置过期时间）
     * @return 自减后的值；异常返回 0
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
     * 增加指定增量并返回增加后的值（负数则为自减，使用默认过期时间，见类注释 TTL 策略）
     *
     * @param key       键，不能为空
     * @param increment 增量
     * @return 增加后的值；异常返回 0
     */
    public Long incrBy(String key, long increment) {
        return incrBy(key, increment, defaultTtl);
    }

    /**
     * 增加指定增量并返回增加后的值（负数则为自减）
     *
     * @param key       键，不能为空
     * @param increment 增量
     * @param timeout   过期时间，必须大于 0；传 {@code null} 表示永久存储（不设置过期时间）
     * @return 增加后的值；异常返回 0
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
     * @param key 键
     * @return 当前值；key为空或异常返回 0
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


    /* ==================== Key 操作 =============================== */

    /**
     * 删除单个 key
     *
     * @param key 键，不能为空
     * @return true 删除成功；false key不存在、参数非法或异常
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
     * @param keys 键集合，不能为空
     * @return true 执行成功（无论删除数量）；false 参数非法或异常
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
     * @param key 键
     * @return true 存在；false 不存在、key为空或异常
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
     * @param key     键，不能为空
     * @param timeout 过期时间，必须大于 0
     * @return true 设置成功；false 参数非法或异常
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
     * @param key     键，不能为空
     * @param timeout 过期时间，必须大于 0
     * @param unit    时间单位，不能为空
     * @return true 设置成功；false 参数非法或异常
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return expire(key, Duration.of(timeout, unit.toChronoUnit()));
    }

    /**
     * 设置过期时间点
     *
     * @param key  键，不能为空
     * @param date 失效时间点，不能为空
     * @return true 设置成功；false 参数非法或异常
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
     *
     * <p>底层精度为毫秒，换算到其他单位时向下取整（截断），如剩余 59.9 秒
     * 以秒返回时为 59
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


    /* ==================== 分布式锁（RLock）======================= */

    /**
     * 在分布式锁内执行业务动作（获取锁失败返回 null 而非抛异常）
     *
     * <p>统一封装「tryLock + finally 解锁」样板，锁 key 自动加 {@code yeed:lock:} 前缀，
     * 无需业务自己处理加锁失败、中断、释放等细节；业务动作抛出的异常向上传播
     * （不吞异常），锁在 finally 中保证释放
     *
     * @param lockKey  锁的业务标识（如 {@code order:123:pay}），不能为空
     * @param waitTime 获取锁的等待时间，不能为空、不能为负；{@code Duration.ZERO} 表示立即尝试
     * @param action   锁内执行的动作，不能为空
     * @param <T>      返回值类型
     * @return action 的执行结果；等待超时未获取到锁返回 null
     * @throws IllegalArgumentException lockKey / waitTime / action 非法时 fail-fast
     *         （锁场景禁止静默忽略，避免无锁执行）
     */
    public <T> T executeWithLock(String lockKey, Duration waitTime, Supplier<T> action) {
        return executeWithLock(lockKey, waitTime, null, action);
    }

    /**
     * 在分布式锁内执行业务动作（获取锁失败返回 null 而非抛异常）
     *
     * <p>统一封装「tryLock + finally 解锁」样板，锁 key 自动加 {@code yeed:lock:} 前缀，
     * 无需业务自己处理加锁失败、中断、释放等细节；业务动作抛出的异常向上传播
     * （不吞异常），锁在 finally 中保证释放
     *
     * @param lockKey   锁的业务标识（如 {@code order:123:pay}），不能为空
     * @param waitTime  获取锁的等待时间，不能为空、不能为负；{@code Duration.ZERO} 表示立即尝试
     * @param leaseTime 锁的持有时间；传 {@code null} 表示启用 Redisson 看门狗自动续期
     *                  （推荐：进程崩溃自动释放，无死锁风险），
     *                  传非空则到期自动释放（业务未在期限内完成可能提前解锁）
     * @param action    锁内执行的动作，不能为空
     * @param <T>       返回值类型
     * @return action 的执行结果；等待超时未获取到锁返回 null
     * @throws IllegalArgumentException lockKey / waitTime / leaseTime / action 非法时 fail-fast
     *         （锁场景禁止静默忽略，避免无锁执行）
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
     * 尝试获取分布式锁（立即返回，不等待）
     *
     * <p>获取成功后需在业务结束后调用 {@link #unlock(String)} 释放；
     * 更推荐使用 {@link #executeWithLock(String, Duration, Supplier)} 自动管理释放
     *
     * @param lockKey  锁的业务标识，不能为空
     * @param waitTime 获取锁的等待时间，不能为空、不能为负；{@code Duration.ZERO} 表示立即尝试
     * @return true 获取成功；false 等待超时未获取到或异常
     * @throws IllegalArgumentException lockKey / waitTime 非法时 fail-fast
     */
    public boolean tryLock(String lockKey, Duration waitTime) {
        return tryLock(lockKey, waitTime, null);
    }

    /**
     * 尝试获取分布式锁
     *
     * <p>获取成功后需在业务结束后调用 {@link #unlock(String)} 释放；
     * 更推荐使用 {@link #executeWithLock(String, Duration, Duration, Supplier)} 自动管理释放
     *
     * @param lockKey   锁的业务标识，不能为空
     * @param waitTime  获取锁的等待时间，不能为空、不能为负；{@code Duration.ZERO} 表示立即尝试
     * @param leaseTime 锁的持有时间；传 {@code null} 表示启用 Redisson 看门狗自动续期，
     *                  传非空则到期自动释放
     * @return true 获取成功；false 等待超时未获取到或异常
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
     * @param lockKey 锁的业务标识，不能为空
     * @return true 释放成功；false 当前线程未持有锁、参数非法或异常
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


    /* ==================== 发布订阅（RTopic，发布端）=============== */

    /**
     * 向指定主题发布消息（仅发布端的薄封装；订阅由业务直接使用 RTopic 管理监听器生命周期）
     *
     * @param topic   主题名，不能为空
     * @param message 消息体，不能为空
     * @return true 发布成功；false 参数非法或异常
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


    /* ==================== 私有辅助 ================================ */

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
