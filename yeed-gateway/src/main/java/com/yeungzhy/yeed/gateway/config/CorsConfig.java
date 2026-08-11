package com.yeungzhy.yeed.gateway.config;

import lombok.NonNull;
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
 * 跨域配置
 *
 * <p>注册 {@link CorsWebFilter} 并设置最高优先级（{@link Ordered#HIGHEST_PRECEDENCE}），
 * 确保在 {@code SaReactorFilter}（{@code HIGHEST_PRECEDENCE + 100}）之前执行。
 *
 * <p>执行顺序：CorsWebFilter 先在响应对象上写入 CORS 头 → 再调 {@code chain.filter} 交给 SaReactorFilter，
 * 这样即使 SaReactorFilter 鉴权失败直接写错误响应体，CORS 头也已就位，浏览器能正常读取错误信息。
 * 预检请求（OPTIONS）由 CorsWebFilter 直接短路返回，不会到达 SaReactorFilter。
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@Configuration
public class CorsConfig {

    /** 给任意 WebFilter 套上优先级，使 Spring WebFlux 按预期顺序执行 */
    private record OrderedWebFilter(WebFilter delegate, int order) implements WebFilter, Ordered {

        @Override
        public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
            return delegate.filter(exchange, chain);
        }

        @Override
        public int getOrder() {
            return order;
        }
    }



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




}
