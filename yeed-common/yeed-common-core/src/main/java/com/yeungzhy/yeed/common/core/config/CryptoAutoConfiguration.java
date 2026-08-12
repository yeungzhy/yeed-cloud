package com.yeungzhy.yeed.common.core.config;

import com.yeungzhy.yeed.common.core.crypto.CryptoProperties;
import com.yeungzhy.yeed.common.core.crypto.CryptoPropertiesValidator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 加密配置自动装配
 *
 * <p>注册 {@link CryptoProperties}（绑定 yaml 中 {@code crypto.*} 配置项）
 * 与 {@link CryptoPropertiesValidator}（启动 fail-fast 校验密钥格式与配对）。
 *
 * <p>归属说明：CryptoProperties / CryptoPropertiesValidator / BlindIndexProvider 工具类
 * 均定义在 common-core，其 Bean 注册也归 common-core 负责，保持"定义与注册在同一模块"。
 * 业务侧的盲索引 Bean（idCard / phone / email 等数据库场景专用）由 common-data 的
 * {@link com.yeungzhy.yeed.common.data.config.BlindIndexAutoConfiguration} 基于 CryptoProperties 注册。
 *
 * @author yeungzhy
 * @since 2026-08-11
 */
@AutoConfiguration
public class CryptoAutoConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "crypto")
    public CryptoProperties cryptoProperties() {
        return new CryptoProperties();
    }

    @Bean
    public CryptoPropertiesValidator cryptoPropertiesValidator() {
        return new CryptoPropertiesValidator();
    }

}
