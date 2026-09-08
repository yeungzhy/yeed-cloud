package com.yeungzhy.yeed.gateway.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.web.reactive.WebHttpHandlerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 链路追踪配置
 *
 * <p>唯一职责：让 WebFlux 应用产生 server span，使每个请求（含被网关直接拒绝的 401/403）
 * 都有 traceId 贯穿始终
 *
 * <p>必须显式配置，Boot 不会自动装配（Spring Boot 3.5.16 实证）：servlet 栈自动注册
 * {@code ServerHttpObservationFilter}，reactive 栈的 {@code WebFluxObservationAutoConfiguration}
 * 只提供观测约定与 MeterFilter，不产生 span：admin 开箱即有 traceId，gateway 默认一个
 * span 都不建，两侧表现不一致，极易误判为"tracing 配置没生效"
 *
 * <p>注入点选 {@link WebHttpHandlerBuilderCustomizer}：reactive 版
 * {@code ServerHttpObservationFilter} 自 Spring Framework 6.1 起标记
 * {@code @Deprecated(forRemoval = true)} 即将删除；官方替代是把 {@link ObservationRegistry}
 * 交给 {@link org.springframework.web.server.adapter.WebHttpHandlerBuilder}，在 WebHandler
 * 层统一埋点
 *
 * <p>不要手动调 {@code Hooks.enableAutomaticContextPropagation()}：那是 Boot
 * {@code ReactorAutoConfiguration} 的职责，由配置 {@code spring.reactor.context-propagation}
 * 驱动（默认 {@code LIMITED} 不启用）；手写 ApplicationRunner 属重复实现，且时机晚于
 * Boot 建链，不可靠
 *
 * <p>SCG 自带 client span（{@code ObservedRequestHttpHeadersFilter}）经
 * {@code parentObservation()} 挂到本配置的 server span 之下形成父子层级；无本配置时退化为
 * 孤立根 span，且仅请求真正转发下游时才创建，401/403 或路由不存在的请求完全没有 traceId
 *
 * <p>建议配 {@code management.tracing.sampling.probability=1.0}，默认 0.1 会让九成日志拿不到 traceId
 *
 * @author yeungzhy
 * @since 2026-08-30
 */
@Configuration(proxyBeanMethods = false)
public class TraceConfig {

    /**
     * 把 {@link ObservationRegistry} 注入 WebHandler 构建链，产生覆盖全请求的 server span
     *
     * <p>{@code @ConditionalOnBean} 是刻意的：未引入 actuator 时无该 Bean，静默跳过而非启动失败；
     */
    @Bean
    @ConditionalOnBean(ObservationRegistry.class)
    public WebHttpHandlerBuilderCustomizer observationRegistryCustomizer(ObservationRegistry observationRegistry) {
        return builder -> builder.observationRegistry(observationRegistry);
    }

}
