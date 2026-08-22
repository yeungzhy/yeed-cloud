package com.yeungzhy.yeed.gateway.config;

import com.yeungzhy.yeed.gateway.filter.AccessLogFilter;
import com.yeungzhy.yeed.gateway.filter.CorsVaryHeadersFilter;
import lombok.NonNull;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter 注册配置
 *
 * <p>统一在此注册网关的自定义 WebFilter，并用 {@link OrderedWebFilter} 显式指定优先级。
 * Spring WebFlux 按 order 从小到大排序执行，数值越小越先执行；order 相同时保持注册顺序，
 * 谁先注册谁先执行，而 {@code @Bean} 方法在配置类里按声明顺序注册。
 *
 * <p>执行顺序：{@link #corsWebFilter()}（注册在前）→ {@link #accessLogWebFilter()}
 * （两者均 {@link Ordered#HIGHEST_PRECEDENCE}）→ SaReactorFilter（源码 {@code @Order(-100)}）
 * → 其余 WebFilter；WebFilter 层全部执行完毕后，才轮到 GlobalFilter 层
 * （网关 {@code GatewayFilterChain} 的路由转发）。注意两层的 order 是各自独立排序、不能混比，
 * GlobalFilter 的 order 再小也不会早于 WebFilter 执行。
 *
 * <p>另注册 Spring Cloud Gateway 的透传响应头过滤器 {@link #corsVaryHeadersFilter()}：
 * 它不属于 WebFilter 层，而是作用于下游响应头的透传合并阶段（见 {@link HttpHeadersFilter}），
 * 与 {@link #corsWebFilter()} 配合保证 CORS Vary 头全局唯一。
 *
 * @author yeungzhy
 * @since 2026-08-21
 */
@Configuration
public class WebFilterConfig {


    /**
     * 用装饰器模式给任意 WebFilter 套上显式优先级，使 Spring WebFlux 按预期顺序执行
     *
     * <p>WebFlux 按 {@link Ordered} 语义对 WebFilter 排序（order 越小越先执行）。
     * 第三方过滤器（如 {@code CorsWebFilter}、Sa-Token {@code SaReactorFilter}）的优先级
     * 不便于直接修改时，用本包装类包裹并指定 order 即可，无需改动第三方实现。
     */
    private record OrderedWebFilter(WebFilter delegate, int order) implements WebFilter, Ordered {

        @Override
        public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
            // 直接把请求"转交"给被包的对象
            return delegate.filter(exchange, chain);
        }

        @Override
        public int getOrder() {
            return order;
        }
    }


    /**
     * 跨域过滤器：最高优先级，确保在 SaReactorFilter 之前执行
     *
     * <p>执行顺序：先在响应对象上写入 CORS 头 → 再交给 SaReactorFilter，这样即使 SaReactorFilter
     * 鉴权失败直接写错误响应体，CORS 头也已就位，浏览器能正常读取错误信息；
     * 预检请求（OPTIONS）由 CorsWebFilter 直接短路返回，不会到达任意 WebFilter
     */
    @Bean
    public WebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new OrderedWebFilter(new CorsWebFilter(source), Ordered.HIGHEST_PRECEDENCE);
    }


    /**
     * 访问日志过滤器：最高优先级，确保在 SaReactorFilter 之前执行，
     * Sa-Token 鉴权拒绝的请求（401/403）同样会进入日志
     */
    @Bean
    public WebFilter accessLogWebFilter() {
        return new OrderedWebFilter(new AccessLogFilter(), Ordered.HIGHEST_PRECEDENCE);
    }


    /**
     * 透传响应头清洗：移除下游响应重复的 CORS Vary 头
     *
     * <p>跨域由 {@link #corsWebFilter()} 统一处理；下游 servlet 服务在 404 兜底时，
     * Spring MVC 静态资源 handler 会无条件追加一组同值 Vary（与是否配置 CORS 无关，
     * 根因见 {@link CorsVaryHeadersFilter}），透传合并后出现重复。本过滤器在透传阶段
     * 仅剔除 CORS 三项，其它 Vary 原样保留。
     */
    @Bean
    public HttpHeadersFilter corsVaryHeadersFilter() {
        return new CorsVaryHeadersFilter();
    }


}
