package com.yeungzhy.yeed.gateway.config;

import com.yeungzhy.yeed.gateway.filter.ApiSecurityFilter;
import com.yeungzhy.yeed.gateway.filter.EnhancedAccessLogFilter;
import com.yeungzhy.yeed.gateway.filter.openapi.OpenapiAppKeyResolver;
import com.yeungzhy.yeed.gateway.filter.openapi.OpenapiCryptoCodec;
import com.yeungzhy.yeed.gateway.filter.openapi.OpenapiReplayGuard;
import com.yeungzhy.yeed.gateway.security.MenuCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import org.springframework.cloud.gateway.filter.AdaptCachedBodyGlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * GlobalFilter 统一注册配置
 *
 * <p>与 {@link WebFilterConfig} 是两层独立的链，order 各自排序、不能混比，
 * WebFilter 层整体早于 GlobalFilter 层执行，GlobalFilter 的 order 再小也不会早于 WebFilter
 *
 * <p>GlobalFilter 执行顺序：
 * <ol>
 *   <li>SCG 内置路由定位过滤器：源码 {@code +100}，先改写请求 URI 指向路由目标
 *   <li>{@code apiSecurityFilter()}：{@code Ordered.HIGHEST_PRECEDENCE + 100}，OpenApi 报文加解密，
 *       必须在访问日志过滤器之前（先解密成明文再记录日志、响应先记日志再加密）
 *   <li>{@code enhancedAccessLogFilter()}：{@code Ordered.HIGHEST_PRECEDENCE + 200}，记录访问日志
 *   <li>SCG 内置 {@link AdaptCachedBodyGlobalFilter}：源码 {@code -2147482648}，请求体换装
 *   <li>SCG 内置路由转发过滤器：NettyRoutingFilter / WebsocketRoutingFilter / ForwardRoutingFilter 等
 * </ol>
 *
 * <p>约束：新增 GlobalFilter 须同步更新本列表，按实际 order 插入，保持注释与实现一致
 *
 * @author yeungzhy
 * @since 2026-08-29
 */
@Configuration
public class GatewayFilterConfig {

    /**
     * 增强型网关访问日志过滤器
     *
     * <p>本过滤器内部已自行完成请求体换装，不再依赖 SCG 内置的 {@link AdaptCachedBodyGlobalFilter}，
     * 故 order 只表达它在项目众多过滤器中的执行顺序，不再是"必须早于谁"的约束
     */
    @Bean
    public EnhancedAccessLogFilter enhancedAccessLogFilter(MenuCache menuCache, Tracer tracer,
                                                           AccessLogExcludeProperties logExcludeProperties) {
        return new EnhancedAccessLogFilter(Ordered.HIGHEST_PRECEDENCE + 200, menuCache, tracer, logExcludeProperties);
    }

    /**
     * OpenApi 报文加解密过滤器
     *
     * <p>只处理公网加密前缀内请求，参数见 {@link ApiSecurityProperties}，
     * 实现步骤与错误码见 {@link ApiSecurityFilter} 类注释；加密头解析、验签、加解密、
     * 防重放与装饰器在 {@code com.yeungzhy.yeed.gateway.filter.openapi} 子包内
     */
    @Bean
    public ApiSecurityFilter apiSecurityFilter(ApiSecurityProperties apiSecurityProperties,
                                               ApiSecurityExcludeProperties apiSecurityExcludeProperties,
                                               OpenapiReplayGuard openapiReplayGuard,
                                               OpenapiAppKeyResolver openapiAppKeyResolver,
                                               OpenapiCryptoCodec openapiCryptoCodec,
                                               ObjectMapper objectMapper) {
        return new ApiSecurityFilter(Ordered.HIGHEST_PRECEDENCE + 100, apiSecurityProperties,
                apiSecurityExcludeProperties, openapiReplayGuard, openapiAppKeyResolver, openapiCryptoCodec, objectMapper);
    }

}
