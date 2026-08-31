package com.yeungzhy.yeed.gateway.config;

import com.yeungzhy.yeed.gateway.filter.EnhancedAccessLogFilter;
import com.yeungzhy.yeed.gateway.security.MenuCache;
import io.micrometer.tracing.Tracer;
import org.springframework.cloud.gateway.filter.AdaptCachedBodyGlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * GlobalFilter 统一注册配置
 *
 * <p>与 {@link WebFilterConfig} 是两层独立的链，order 各自排序、不能混比；
 * WebFilter 层整体早于 GlobalFilter 层执行，GlobalFilter 的 order 再小也不会早于 WebFilter。
 *
 * <p>GlobalFilter 执行顺序：</p>
 * <ol>
 *   <li>SCG 内置路由定位过滤器 —— 源码 {@code +100}，先改写请求 URI 指向路由目标</li>
 *   <li>{@code enhancedAccessLogFilter()} —— {@code Ordered.HIGHEST_PRECEDENCE + 200}，记录访问日志</li>
 *   <li>SCG 内置 {@link AdaptCachedBodyGlobalFilter} —— 源码 {@code -2147482648}，请求体换装</li>
 *   <li>SCG 内置路由转发过滤器 —— NettyRoutingFilter / WebsocketRoutingFilter / ForwardRoutingFilter 等</li>
 * </ol>
 *
 * <p>约束：新增 GlobalFilter 时须同步更新本列表，按实际 order 插入，保持注释与实现一致
 *
 * @author yeungzhy
 * @since 2026-08-29
 */
@Configuration
public class GatewayFilterConfig {

    /**
     * 增强型网关访问日志过滤器
     *
     * <p>该过滤器内部已实现请求体换装, 如若依赖 SCG 内置的 {@link AdaptCachedBodyGlobalFilter}, 需要
     * Order 比其更小, 以确保请求体被缓存, 否则会导致请求挂死直到超时
     *
     * <p>内部实现请求体换装之后, 不再依赖 SCG 内置的 {@link AdaptCachedBodyGlobalFilter},
     * Order 语义只是项目中众多过滤器的执行顺序
     */
    @Bean
    public EnhancedAccessLogFilter enhancedAccessLogFilter(MenuCache menuCache, Tracer tracer,
                                                           AccessLogExcludeProperties logExcludeProperties) {
        return new EnhancedAccessLogFilter(Ordered.HIGHEST_PRECEDENCE + 200, menuCache, tracer, logExcludeProperties);
    }

}
