package com.yeungzhy.yeed.common.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeungzhy.yeed.common.core.support.JacksonMapperRegistrar;
import com.yeungzhy.yeed.common.core.support.JacksonUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Bean;

/**
 * Jackson 全局配置
 *
 * <p>定义全局唯一的 {@link ObjectMapper} Bean，统一时间格式化、Long→String 等序列化行为。
 * 归属 common-core：所有引入 core 的模块（admin / auth / gateway）均自动获得同一份配置，
 * cache 模块的 {@code RedisTemplate} 序列化、security 模块的类型转换、web 层的 HTTP JSON
 * 序列化全部复用同一个实例。
 *
 * <p>{@link JacksonMapperRegistrar} 负责把 {@link ObjectMapper} Bean 绑定到 {@link JacksonUtil}，
 * 使静态调用与容器各层共用同一实例
 *
 * @author yeungzhy
 */
@AutoConfiguration
@AutoConfigureBefore(org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class)
public class JacksonAutoConfiguration {

    /**
     * 自定义 ObjectMapper 替换 Spring Boot 默认实例, 覆盖默认行为
     * <p>配置由 {@link JacksonUtil#newDefaultMapper()} 统一提供，本方法不重复定义
     */
    @Bean
    public ObjectMapper objectMapper() {
        return JacksonUtil.newDefaultMapper();
    }

    /**
     * 注册 {@link JacksonMapperRegistrar}，把上方 {@link ObjectMapper} 绑定到 {@link JacksonUtil}。
     * 业务侧直接静态调用 {@code JacksonUtil.toJsonStr(obj)} 即可，无需注入任何 Bean。
     */
    @Bean
    public JacksonMapperRegistrar jacksonMapperRegistrar(ObjectMapper objectMapper) {
        return new JacksonMapperRegistrar(objectMapper);
    }

}
