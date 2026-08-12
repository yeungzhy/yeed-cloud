package com.yeungzhy.yeed.common.cache.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeungzhy.yeed.common.cache.support.RedisHelper;
import jakarta.annotation.Resource;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis / Redisson 自动配置
 *
 * <p>以全局 {@link ObjectMapper} 为唯一序列化基准，统一三块能力：
 * <ul>
 *     <li>{@link RedisTemplate}：Key 用 String 序列化器（可读），Value 用
 *     {@link GenericJackson2JsonRedisSerializer}（写入 {@code @class} 类型信息）</li>
 *     <li>Redisson：{@link JsonJacksonCodec} 编解码器，与 RedisTemplate 共用同一
 *     ObjectMapper，时间格式、Long→String 等映射保持一致</li>
 *     <li>{@link RedisHelper}：面向业务封装 RBucket/RList/RSet/RMap 等常用缓存操作</li>
 * </ul>
 *
 * <p>通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 注册，并声明在 Spring Boot 自带
 * {@link org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration} 之前生效
 *
 * @author yeungzhy
 * @since 2026-08-11
 */
@AutoConfiguration(before = org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class)
public class RedisAutoConfiguration {

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 配置 {@link RedisTemplate}：Key 用 String 序列化器、Value 用 JSON 序列化器
     *
     * <p>RedisConnectionFactory 以方法参数注入（而非字段注入），避免与
     * RedissonAutoConfigurationV2 形成循环依赖：该配置类创建时需要加载本类提供的
     * Customizer，若本类又字段注入其创建的 ConnectionFactory 则互相等待；
     * 改为方法参数注入后，依赖延后到 Bean 方法调用时才解析，循环自然解开。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();

        /*
         * Key   使用 String 序列化器，Redis 中以可读字符串展示
         * Value 使用 JSON   序列化器，对象转 JSON 存储
         * GenericJackson2JsonRedisSerializer 会把对象类信息写入 JSON（@class），便于反序列化还原类型
         */
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
     * 定制 Redisson 编解码器：使用全局 {@link ObjectMapper}
     * <p>使 RBucket/RMap/RList/RTopic 等 Redisson 操作的序列化行为与 RedisTemplate
     * 共用同一套配置（时间格式、Long→String 映射等）
     *
     * <p>注意：两者序列化产物不同——RedisTemplate 用 GenericJackson2JsonRedisSerializer
     * 会写入 {@code @class} 类型信息，Redisson 的 JsonJacksonCodec 不会。
     * 实际使用中两者各管各的 key、不会交叉读写，因此不冲突。
     */
    @Bean
    public RedissonAutoConfigurationCustomizer redissonCustomizer() {
        return config -> config.setCodec(new JsonJacksonCodec(objectMapper));
    }

    /**
     * 注册 {@link RedisHelper}，业务侧注入后即可使用封装的缓存操作
     */
    @Bean
    public RedisHelper redisHelper(RedissonClient redissonClient) {
        return new RedisHelper(redissonClient);
    }

}
