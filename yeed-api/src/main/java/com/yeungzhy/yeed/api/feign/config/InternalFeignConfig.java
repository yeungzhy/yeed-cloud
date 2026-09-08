package com.yeungzhy.yeed.api.feign.config;

import com.yeungzhy.yeed.api.feign.decoder.InternalErrorDecoder;
import com.yeungzhy.yeed.api.feign.interceptor.InternalTokenRelayInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * 内部 Feign 契约的 FeignClient 级装配（被 {@code @FeignClient(configuration = ...)} 引用）
 *
 * <p>不是 Spring 配置类：本类故意不写 {@code @Configuration}、也不进组件扫描，
 * 只应被实例化进引用它的各 Feign 子容器；写成 {@code @Configuration} 或加 {@code @Component}
 * 会让两个扩展点变成全局 Bean，波及其余 Feign 客户端
 *
 * <p>两个扩展点同属内部契约语义，按 Feign 扩展点类型分包：
 * {@link InternalTokenRelayInterceptor}（{@code feign.interceptor}）、
 * {@link InternalErrorDecoder}（{@code feign.decoder}），本包只留装配入口
 *
 * @author yeungzhy
 * @since 2026-09-04
 */
public class InternalFeignConfig {

    /**
     * token 透传拦截器（Bean 级：仅内部契约客户端使用）
     *
     * @return 每次装配新建实例，无状态、可安全共享于该 Feign 子容器
     */
    @Bean
    public InternalTokenRelayInterceptor internalTokenRelayInterceptor() {
        return new InternalTokenRelayInterceptor();
    }

    /**
     * 内部契约错误解码：下游 HTTP 错误 → BizException（码 + 话术透传）
     *
     * @return 每次装配新建实例，无状态、可安全共享于该 Feign 子容器
     */
    @Bean
    public InternalErrorDecoder internalErrorDecoder() {
        return new InternalErrorDecoder();
    }

}
