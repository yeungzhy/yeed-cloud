package com.yeungzhy.yeed.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.yeungzhy.yeed.common.mybatis.MybatisPlusAutoFillFieldHandler;
import com.yeungzhy.yeed.common.mybatis.MybatisPlusCustomIdGenerator;
import com.yeungzhy.yeed.common.mybatis.MybatisPlusCustomIdProperties;
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
    @ConfigurationProperties(prefix = "mybatis-plus.global-config")
    public MybatisPlusCustomIdProperties mybatisPlusCustomIdProperties() {
        // 返回一个空实例，Spring 会自动调用绑定器填充属性
        return new MybatisPlusCustomIdProperties();
    }


    @Bean
    public MybatisPlusCustomIdGenerator mybatisPlusCustomIdGenerator(MybatisPlusCustomIdProperties properties) {
        return new MybatisPlusCustomIdGenerator(properties.getWorkerId(), properties.getDataCenterId());
    }


    @Bean
    public MybatisPlusAutoFillFieldHandler mybatisPlusAutoFillFieldHandler() {
        return new MybatisPlusAutoFillFieldHandler();
    }



}
