package com.yeungzhy.yeed.common.web.config;

import com.yeungzhy.yeed.common.web.bootstrap.InternalApiValidator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * {@link InternalApiValidator} 自动装配
 *
 * <p> 注册启动期契约守卫：校验内部 RPC-Style 端点的路径前缀与 {@code @InternalApi} 注解一致性。
 * 引入 yeed-common-web 即生效，无内部端点的应用空转无害
 *
 * <p> 为何必须在此显式装配：校验器位于 common-web 的 {@code bootstrap} 包，不在任何业务模块的
 * 组件扫描边界（{@code com.yeungzhy.yeed.<module>}）内；若仅靠 {@code @Component} 而不登记进
 * {@code AutoConfiguration.imports}，守卫会静默失效，与 {@link ExceptionHandlerAutoConfiguration}
 * 中 Advice 必须显式注册是同一问题
 *
 * @author yeungzhy
 * @since 2026-09-04
 * @see InternalApiValidator
 */
@AutoConfiguration
public class InternalApiValidatorAutoConfiguration {

    /**
     * 注册启动期契约校验守卫
     *
     * @param applicationContext Spring 容器上下文，用于检索全部 Controller Bean
     * @return 校验守卫单例 Bean
     */
    @Bean
    public InternalApiValidator internalApiValidator(ApplicationContext applicationContext) {
        return new InternalApiValidator(applicationContext);
    }

}
