package com.yeungzhy.yeed.common.web.config;

import com.yeungzhy.yeed.common.web.bootstrap.InternalApiValidator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * InternalApiValidator 自动装配
 *
 * <p>注册 {@link InternalApiValidator}（启动期校验内部 RPC-Style 端点的路径前缀与
 * {@code @InternalApi} 注解一致性）。
 *
 * <p>为何必须在此显式装配：校验器位于 common-web 的 {@code bootstrap} 包，各业务模块的组件扫描
 * 边界（{@code com.yeungzhy.yeed.<module>}）扫不到；若仅靠 {@code @Component} 而不登记进
 * {@code AutoConfiguration.imports}，守卫会静默失效——与 {@link ExceptionHandlerAutoConfiguration}
 * 中 Advice 必须显式注册是同一问题。下游模块引入 yeed-common-web 即生效，无内部端点的应用空转无害。
 *
 * @author yeungzhy
 * @since 2026-09-04
 * @see InternalApiValidator
 */
@AutoConfiguration
public class InternalApiValidatorAutoConfiguration {

    @Bean
    public InternalApiValidator internalApiValidator(ApplicationContext applicationContext) {
        return new InternalApiValidator(applicationContext);
    }

}
