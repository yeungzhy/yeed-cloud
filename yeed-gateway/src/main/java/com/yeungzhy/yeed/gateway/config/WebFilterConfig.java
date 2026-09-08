package com.yeungzhy.yeed.gateway.config;

import com.yeungzhy.yeed.gateway.filter.AccessLogFilter;
import com.yeungzhy.yeed.gateway.filter.CorsVaryHeadersFilter;
import com.yeungzhy.yeed.gateway.filter.LoginUserSnapshotFilter;
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
 * <p>统一在此注册网关的自定义 WebFilter，并用 {@link OrderedWebFilter} 显式指定优先级
 *
 * <p>Spring WebFlux 按 order 从小到大排序执行，数值越小越先执行；order 相同时按注册顺序，
 * 而 {@code @Bean} 方法在配置类里按声明顺序注册
 *
 * <p>WebFilter 层全部执行完毕后才轮到 GlobalFilter 层（网关 {@code GatewayFilterChain} 的路由转发），
 * 两层的 order 各自排序、不能混比，GlobalFilter 的 order 再小也不会早于 WebFilter 执行
 *
 * <p>WebFilter 执行顺序：
 * <ol>
 *   <li>{@link #corsWebFilter()}：{@code Ordered.HIGHEST_PRECEDENCE}，注册在最前
 *   <li>{@link #loginUserSnapshotFilter()}：{@code +10}
 *   <li>{@link #accessLogWebFilter()}：{@code +20}
 *   <li>{@link cn.dev33.satoken.reactor.filter.SaReactorFilter}：源码 {@code @Order(-100)}
 *   <li>其余 WebFilter
 * </ol>
 *
 * <p>约束：新增 WebFilter 须同步更新本列表，按实际 order 插入，保持注释与实现一致
 *
 * @author yeungzhy
 * @since 2026-08-21
 */
@Configuration
public class WebFilterConfig {


    /**
     * 用装饰器模式给任意 WebFilter 套上显式优先级，使 Spring WebFlux 按预期顺序执行
     *
     * <p>WebFlux 按 {@link Ordered} 语义对 WebFilter 排序（order 越小越先执行）；
     * 第三方过滤器（如 {@code CorsWebFilter}、Sa-Token {@code SaReactorFilter}）的优先级
     * 不便于直接修改时，用本包装类包裹并指定 order 即可，无需改动第三方实现；
     */
    private record OrderedWebFilter(WebFilter delegate, int order) implements WebFilter, Ordered {
        @Override
        @SuppressWarnings("NullableProblems")
        public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
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
     * <p>执行顺序：先在响应对象上写入 CORS 头、再交给 SaReactorFilter，这样即使鉴权失败直接写错误响应体，
     * CORS 头也已就位，浏览器能正常读到错误信息；预检请求（OPTIONS）由 CorsWebFilter 直接短路返回，
     * 到不了任何 WebFilter
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
     * 登录用户快照：在 SaReactorFilter 之前捕获身份，供 GlobalFilter 层复用
     *
     * <p>order 取 {@code HIGHEST_PRECEDENCE + 10}（远小于 SaReactorFilter 的 {@code -100}）：
     * 鉴权失败（401/403）的请求不再走后续链路，排在其后就拿不到身份，
     * 而审计要记录"谁尝试了越权访问"，故必须前置于鉴权
     *
     * <p>捕获结果由 {@link LoginUserSnapshotFilter#LOGIN_USER_SNAPSHOT_KEY} 传递，
     * 消费者见 {@link com.yeungzhy.yeed.gateway.filter.EnhancedAccessLogFilter}；
     */
    @Bean
    public WebFilter loginUserSnapshotFilter() {
        return new OrderedWebFilter(new LoginUserSnapshotFilter(), Ordered.HIGHEST_PRECEDENCE + 10);
    }


    /**
     * 访问日志过滤器：晚于 {@link #loginUserSnapshotFilter()} 以复用其身份快照，
     * 仍远早于 SaReactorFilter（{@code -100}），故鉴权拒绝的请求（401/403）同样会进入日志
     */
    @Bean
    public WebFilter accessLogWebFilter() {
        return new OrderedWebFilter(new AccessLogFilter(), Ordered.HIGHEST_PRECEDENCE + 20);
    }


    /**
     * 透传响应头清洗：移除下游响应重复的 CORS Vary 头
     *
     * <p>跨域由 {@link #corsWebFilter()} 统一处理；下游 servlet 服务在 404 兜底时，
     * Spring MVC 静态资源 handler 会无条件追加一组同值 Vary（与是否配置 CORS 无关，
     * 根因见 {@link CorsVaryHeadersFilter}），透传合并后出现重复。本过滤器在透传阶段
     * 仅剔除 CORS 三项，其它 Vary 原样保留；
     */
    @Bean
    public HttpHeadersFilter corsVaryHeadersFilter() {
        return new CorsVaryHeadersFilter();
    }


}
