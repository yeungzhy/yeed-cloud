package com.yeungzhy.yeed.api;

import com.yeungzhy.yeed.api.feign.EnableFeignFallbacks;
import com.yeungzhy.yeed.api.feign.interceptor.FeignTokenRelayInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

/**
 * Feign 客户端集中注册入口（自动配置类）。
 *
 * <p>本类位于 yeed-api 模块，通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 被 Spring Boot 自动装配机制加载。任何依赖 yeed-api 的服务只要 classpath 引入了本模块，
 * 即可自动获得所有 {@code @FeignClient} 接口的代理 Bean，无需在各自启动类上额外标注
 * {@link EnableFeignClients}。
 *
 * <p>由三个注解完成一次性装配：
 * <ul>
 *   <li>{@code @AutoConfiguration}：声明自动配置身份，由 Spring Boot 启动时加载本类</li>
 *   <li>{@link EnableFeignFallbacks}：开启 {@link com.yeungzhy.yeed.api.feign.FeignFallback FeignFallback}
 *       兜底实现的扫描与注册，免去在配置类中逐个 {@code @Import} 兜底类</li>
 *   <li>{@link EnableFeignClients}：扫描并注册根包下全部 {@code @FeignClient} 代理 Bean</li>
 * </ul>
 *
 * <p>扫描范围统一限定为 {@code com.yeungzhy.yeed.api}：所有跨服务 Feign 契约收纳于此根包下的
 * {@code *.feign} 子包，新增 Feign 客户端或兜底类时只需遵循该约定，
 * 无需修改本类或任何消费方启动类。
 *
 * @author yeungzhy
 * @since 2026-08-09
 * @see com.yeungzhy.yeed.api.feign.EnableFeignFallbacks 兜底扫描总开关
 * @see com.yeungzhy.yeed.api.feign.FeignFallbacksRegistrar 兜底类扫描与注册实现
 * @see com.yeungzhy.yeed.api.feign.FeignFallback 兜底类标记注解
 */
@AutoConfiguration
@EnableFeignFallbacks
@EnableFeignClients("com.yeungzhy.yeed.api")
public class FeignClientsAutoConfiguration {

    /**
     * 通用 Feign 请求头透传：把当前请求的 token 透传到下游 Feign 调用。
     *
     * <p>必须显式 {@code @Bean} 注册——yeed-api 为自动装配包，消费方组件扫描
     * 扫不到本模块的 {@code @Component}，仅靠类上注解不会生效。
     *
     * @return 透传拦截器实例
     */
    @Bean
    public FeignTokenRelayInterceptor feignTokenRelayInterceptor() {
        return new FeignTokenRelayInterceptor();
    }

}
