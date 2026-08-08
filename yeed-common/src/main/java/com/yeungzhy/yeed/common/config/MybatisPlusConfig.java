package com.yeungzhy.yeed.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.yeungzhy.yeed.common.crypto.CryptoProperties;
import com.yeungzhy.yeed.common.mybatis.AutoFillFieldHandler;
import com.yeungzhy.yeed.common.mybatis.CustomIdGenerator;
import com.yeungzhy.yeed.common.mybatis.CustomIdProperties;
import com.yeungzhy.yeed.common.mybatis.FieldCryptoInterceptor;
import com.yeungzhy.yeed.common.mybatis.CustomSqlInjector;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        /*
         * 如果配置多个插件, 切记分页最后添加
         * 如果有多数据源可以不配具体类型, 否则都建议配上具体的 DbType
         */
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
    public CustomSqlInjector yeedSqlInjector() {
        return new CustomSqlInjector();
    }


}
