package com.yeungzhy.yeed.common.cache.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@AutoConfiguration
public class RedisConfig {

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
}
