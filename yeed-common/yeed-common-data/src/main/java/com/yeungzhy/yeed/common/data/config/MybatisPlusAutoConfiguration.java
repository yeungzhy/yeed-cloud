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
 * MyBatis-Plus 自动配置：集中注册项目所需的扩展 Bean
 *
 * <p> 注册项：
 * <ul>
 *   <li>MyBatis-Plus 拦截器：乐观锁 + 分页（MySQL）
 *   <li>{@link AutoFillFieldHandler}：审计字段自动填充（createBy/createTime/updateBy/updateTime）
 *   <li>{@link CustomIdProperties}/{@link CustomIdGenerator}：雪花 ID 的 workerId/dataCenterId 配置
 *   <li>{@link FieldCryptoInterceptor}：{@code @Crypto} 字段 AES 加解密拦截器
 *   <li>{@link CustomSqlInjector}：注入项目自定义 SQL 方法
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-08-02
 */
@AutoConfiguration
public class MybatisPlusAutoConfiguration {

    /**
     * 注册 MyBatis-Plus 插件链：乐观锁 + 分页
     *
     * @return 插件链
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 分页插件必须加在链尾：排在它后面的拦截器拿不到改写后的分页 SQL
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }


    /**
     * 注册审计字段自动填充处理器
     *
     * @return createBy / createTime / updateBy / updateTime 的填充器
     */
    @Bean
    public AutoFillFieldHandler autoFillFieldHandler() {
        return new AutoFillFieldHandler();
    }


    /**
     * 绑定雪花 ID 的 workerId / dataCenterId 配置
     *
     * <p> 用 {@code @ConfigurationProperties} 而非构造器参数，是为了让 workerId / dataCenterId
     * 走统一的松弛绑定（{@code data-center-id}、{@code DATA_CENTER_ID} 都能识别）；
     * 这两个键属必填，缺失时会在 {@link #customIdGenerator(CustomIdProperties)} 拆箱处抛 NPE
     *
     * @return 绑定后的配置，属性由 Spring 填充
     */
    @Bean
    @ConfigurationProperties(prefix = "mybatis-plus.global-config")
    public CustomIdProperties customIdProperties() {
        // 返回一个空实例，Spring 会自动调用绑定器填充属性
        return new CustomIdProperties();
    }


    /**
     * 注册雪花 ID 生成器
     *
     * @param properties workerId / dataCenterId 配置，不能为 null
     * @return 固定机器号的雪花 ID 生成器
     */
    @Bean
    public CustomIdGenerator customIdGenerator(CustomIdProperties properties) {
        return new CustomIdGenerator(properties.getWorkerId(), properties.getDataCenterId());
    }


    /**
     * 注册 {@code @Crypto} 字段加解密拦截器
     *
     * @param cryptoProperties AES 配置，密钥来源，不能为 null
     * @return 字段加解密拦截器
     */
    @Bean
    public FieldCryptoInterceptor fieldCryptoInterceptor(CryptoProperties cryptoProperties) {
        return new FieldCryptoInterceptor(cryptoProperties);
    }


    /**
     * 注册自定义 SQL 注入器
     *
     * @return 追加了本项目扩展方法的注入器
     */
    @Bean
    public CustomSqlInjector customSqlInjector() {
        return new CustomSqlInjector();
    }


}
