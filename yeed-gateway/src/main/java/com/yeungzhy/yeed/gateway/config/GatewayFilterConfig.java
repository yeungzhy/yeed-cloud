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
 * <p>与 {@link WebFilterConfig} 是两层独立的链，order 各自排序、不能混比，
 * WebFilter 层整体早于 GlobalFilter 层执行，GlobalFilter 的 order 再小也不会早于 WebFilter
 *
 * <p>GlobalFilter 执行顺序：
 * <ol>
 *   <li>SCG 内置路由定位过滤器：源码 {@code +100}，先改写请求 URI 指向路由目标
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

}
