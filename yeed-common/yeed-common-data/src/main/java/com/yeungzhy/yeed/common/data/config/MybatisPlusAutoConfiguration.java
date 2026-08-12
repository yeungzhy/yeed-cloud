package com.yeungzhy.yeed.common.data.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.yeungzhy.yeed.common.core.crypto.CryptoProperties;
import com.yeungzhy.yeed.common.data.mybatis.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis-Plus 自动配置：集中注册项目所需的扩展 Bean。
 *
 * <p>注册项：
 * <ul>
 *   <li>MyBatis-Plus 拦截器：乐观锁 + 分页（MySQL）</li>
 *   <li>{@link AutoFillFieldHandler}：审计字段自动填充（createBy/createTime/updateBy/updateTime）</li>
 *   <li>{@link CustomIdProperties}/{@link CustomIdGenerator}：雪花 ID 的 workerId/dataCenterId 配置</li>
 *   <li>{@link FieldCryptoInterceptor}：{@code @Crypto} 字段 AES 加解密拦截器</li>
 *   <li>{@link CustomSqlInjector}：注入项目自定义 SQL 方法</li>
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-08-02
 */
@AutoConfiguration
public class MybatisPlusAutoConfiguration {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 分页插件必须在拦截器链最后添加，且建议显式指定 DbType（多数据源可不配）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }


    @Bean
    public AutoFillFieldHandler autoFillFieldHandler() {
        return new AutoFillFieldHandler();
    }


    @Bean
    @ConfigurationProperties(prefix = "mybatis-plus.global-config")
    public CustomIdProperties customIdProperties() {
        // 返回一个空实例，Spring 会自动调用绑定器填充属性
        return new CustomIdProperties();
    }


    @Bean
    public CustomIdGenerator customIdGenerator(CustomIdProperties properties) {
        return new CustomIdGenerator(properties.getWorkerId(), properties.getDataCenterId());
    }


    @Bean
    public FieldCryptoInterceptor fieldCryptoInterceptor(CryptoProperties cryptoProperties) {
        return new FieldCryptoInterceptor(cryptoProperties);
    }


    @Bean
    public CustomSqlInjector customSqlInjector() {
        return new CustomSqlInjector();
    }


}
