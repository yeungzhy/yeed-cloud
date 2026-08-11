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

@AutoConfiguration(before = org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class)
public class RedisAutoConfiguration {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    /**
     * 配置 RedisTemplate
     * 默认的 RedisTemplate 使用的是 JdkSerializationRedisSerializer，存储二进制字节，不易阅读
     * 我们需要自定义 Key 和 Value 的序列化方式
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        
        /*
         * Key   使用 String 序列化器，Key 在 Redis 里是可读的字符串
         * Value 使用 JSON   序列化器，将对象转为 JSON 格式存储
         * GenericJackson2JsonRedisSerializer 会将对象的类信息也存入 JSON，方便反序列化
         */
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonRedisSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        // Key/Value 序列化
        template.setKeySerializer(stringRedisSerializer);
        template.setValueSerializer(jsonRedisSerializer);

        // Hash 的 Key/Value 序列化
        template.setHashKeySerializer(stringRedisSerializer);
        template.setHashValueSerializer(jsonRedisSerializer);

        // 初始化参数
        template.setConnectionFactory(redisConnectionFactory);
        template.afterPropertiesSet();

        return template;
    }

    /**
     * 定制 Redisson 编解码器：使用全局 ObjectMapper
     * <p>使 Redisson 的 RBucket/RMap/RTopic 等操作的序列化行为
     * 与 RedisTemplate 保持一致（时间格式、Long→String 等）
     *
     * <p>注意：RedisTemplate 使用 GenericJackson2JsonRedisSerializer（带 @class 类型信息），
     * Redisson 使用 JsonJacksonCodec（不带类型信息），两者序列化结果不同。
     * 但实际使用中 RedisTemplate 和 Redisson 各管各的 key，不会交叉读写，所以不冲突。
     */
    @Bean
    public RedissonAutoConfigurationCustomizer redissonCustomizer() {
        return config -> config.setCodec(new JsonJacksonCodec(objectMapper));
    }

    /**
     * 注册 {@link RedisHelper} 工具 Bean，业务侧通过 {@code @Resource RedisHelper redisHelper} 注入即可使用
     */
    @Bean
    public RedisHelper redisHelper(RedissonClient redissonClient) {
        return new RedisHelper(redissonClient);
    }


}
