package com.yeungzhy.yeed.common.data.config;

import com.yeungzhy.yeed.common.core.config.CryptoAutoConfiguration;
import com.yeungzhy.yeed.common.core.crypto.BlindIndexProvider;
import com.yeungzhy.yeed.common.core.crypto.CryptoProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;

/**
 * 数据库盲索引自动装配
 *
 * <p> 基于 common-core 的 {@link CryptoProperties} 注册 data 层专用的盲索引 Bean，
 * 供 {@code FieldCryptoInterceptor} 入库加密、Service 层等值查询条件转换使用
 *
 * <p> 归属说明：盲索引的"算法工具类" {@link BlindIndexProvider} 在 common-core，
 * "具体字段的盲索引 Bean"是数据库场景专用，注册在 common-data；
 * 依赖 {@link CryptoAutoConfiguration} 注册的 CryptoProperties Bean，故显式声明加载顺序
 *
 * <p> 当前预设 idCard / phone / email 三个 PII 字段；如需扩展（如 bankCard），
 * 可在业务模块自定义 {@code @Configuration} 追加 {@code BlindIndexProvider} Bean
 *
 * @author yeungzhy
 * @since 2026-08-11
 */
@AutoConfiguration
@AutoConfigureAfter(CryptoAutoConfiguration.class)
public class BlindIndexAutoConfiguration {

    /**
     * 身份证盲索引
     *
     * @param cryptoProperties 加密配置，提供 HMAC 密钥，不能为 null
     * @return 身份证字段专用的盲索引计算器
     */
    @Bean
    public BlindIndexProvider idCardBlindIndex(CryptoProperties cryptoProperties) {
        return new BlindIndexProvider(cryptoProperties.getHmac().getSecret(), "idCard");
    }

    /**
     * 手机号盲索引
     *
     * @param cryptoProperties 加密配置，提供 HMAC 密钥，不能为 null
     * @return 手机号字段专用的盲索引计算器
     */
    @Bean
    public BlindIndexProvider phoneBlindIndex(CryptoProperties cryptoProperties) {
        return new BlindIndexProvider(cryptoProperties.getHmac().getSecret(), "phone");
    }

    /**
     * 邮箱盲索引
     *
     * <p> 入参须先归一化（trim + 转小写）再算，否则大小写不同会算出不同哈希，唯一索引形同虚设
     *
     * @param cryptoProperties 加密配置，提供 HMAC 密钥，不能为 null
     * @return 邮箱字段专用的盲索引计算器
     */
    @Bean
    public BlindIndexProvider emailBlindIndex(CryptoProperties cryptoProperties) {
        return new BlindIndexProvider(cryptoProperties.getHmac().getSecret(), "email");
    }

}
