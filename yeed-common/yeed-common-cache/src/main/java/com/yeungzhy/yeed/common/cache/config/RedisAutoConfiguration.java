package com.yeungzhy.yeed.common.cache.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeungzhy.yeed.common.cache.support.RedisHelper;
import jakarta.annotation.Resource;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis / Redisson 自动配置
 *
 * <p> 以全局 {@link ObjectMapper} 为唯一序列化基准，统一三块能力：
 * <ul>
 *     <li>{@link RedisTemplate}：Key 用 String 序列化器（可读），Value 用
 *     {@link GenericJackson2JsonRedisSerializer}（写入 {@code @class} 类型信息）
 *     <li>Redisson：{@link JsonJacksonCodec} 编解码器，与 RedisTemplate 共用同一
 *     ObjectMapper，时间格式、Long→String 等映射保持一致
 *     <li>{@link RedisHelper}：面向业务封装 RBucket/RList/RSet/RMap 等常用缓存操作
 * </ul>
 *
 * <p> 通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 注册，并声明在 Spring Boot 自带
 * {@link org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration} 之前生效
 *
 * @author yeungzhy
 * @since 2026-08-11
 */
@AutoConfiguration(before = org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class)
@EnableConfigurationProperties(CacheProperties.class)
public class RedisAutoConfiguration {

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 配置 {@link RedisTemplate}
     *
     * <p> Key 用 String 序列化器，Redis 里是可读字符串，便于命令行排查；
     * Value 用 GenericJackson2JsonRedisSerializer 而非 Jackson2JsonRedisSerializer，
     * 前者会把 @class 类型信息写进 JSON，反序列化才能还原成原类型
     *
     * <p> RedisConnectionFactory 走方法参数注入而非字段注入，避免与 RedissonAutoConfigurationV2 循环依赖：
     * 该配置类创建时要加载本类提供的 Customizer，本类若字段注入它创建的 ConnectionFactory 则互相等待；
     * 改成参数注入后，依赖延后到 Bean 方法调用时才解析
     *
     * @param redisConnectionFactory 连接工厂，由 Redisson 提供
     * @return 配置好序列化器的 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();

        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonRedisSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        template.setKeySerializer(stringRedisSerializer);
        template.setValueSerializer(jsonRedisSerializer);

        template.setHashKeySerializer(stringRedisSerializer);
        template.setHashValueSerializer(jsonRedisSerializer);

        template.setConnectionFactory(redisConnectionFactory);
        template.afterPropertiesSet();

        return template;
    }

    /**
     * 定制 Redisson 编解码器：复用全局 {@link ObjectMapper}
     *
     * <p> 让 RBucket / RMap / RList / RTopic 等 Redisson 操作与 {@link RedisTemplate}
     * 共用一套序列化配置（时间格式、Long→String 映射等）
     *
     * <p> 两边产物并不相同：RedisTemplate 会写 {@code @class}，JsonJacksonCodec 不写；
     * 二者各管各的 key、不交叉读写，所以无需统一
     *
     * @return Redisson 自动配置定制器
     */
    @Bean
    public RedissonAutoConfigurationCustomizer redissonCustomizer() {
        return config -> config.setCodec(new JsonJacksonCodec(objectMapper));
    }

    /**
     * 注册 {@link RedisHelper}，业务侧注入后即可使用封装的缓存操作
     *
     * <p> 默认过期时间取 {@link CacheProperties}（{@code yeed.cache.default-ttl}，未配置默认 24 小时）：
     * 写方法不显式传过期时间时统一兜底，防止数据永久堆积
     *
     * @param redissonClient   Redisson 客户端，由 Redisson 自动配置提供
     * @param cacheProperties 缓存配置，默认 TTL 的来源
     * @return 可直接注入使用的 RedisHelper 单例
     */
    @Bean
    public RedisHelper redisHelper(RedissonClient redissonClient, CacheProperties cacheProperties) {
        return new RedisHelper(redissonClient, cacheProperties.getDefaultTtl());
    }

}
