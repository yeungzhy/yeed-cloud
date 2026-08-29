package com.yeungzhy.yeed.common.core.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeungzhy.yeed.common.core.config.JacksonAutoConfiguration;
import org.springframework.beans.factory.InitializingBean;

/**
 * ObjectMapper 绑定器
 *
 * <p>由 {@link JacksonAutoConfiguration} 注册，在容器初始化阶段（{@link InitializingBean#afterPropertiesSet()}）
 * 把容器中<b>生效的</b> {@link ObjectMapper} Bean 绑定到 {@link JacksonUtil} 静态字段，使项目内各层共用同一个实例（共享序列化器缓存）
 *
 * @see JacksonUtil
 * @see JacksonAutoConfiguration
 */
public class JacksonMapperRegistrar implements InitializingBean {

    private final ObjectMapper objectMapper;

    public JacksonMapperRegistrar(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterPropertiesSet() {
        JacksonUtil.bind(objectMapper);
    }
}
