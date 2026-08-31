package com.yeungzhy.yeed.gateway.filter;

import com.yeungzhy.yeed.common.core.constant.Constant;
import com.yeungzhy.yeed.common.core.security.LoginUserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 访问日志过滤器（WebFilter 层）
 *
 * <p>记录每个请求：时间、用户、姓名、方法、路径、状态码、耗时
 *
 * <p>耗时在 {@code doFinally} 中统计，覆盖整个下游转发 + 响应阶段
 *
 * @author yeungzhy
 * @since 2026-08-16
 */
@Slf4j
public class AccessLogFilter implements WebFilter {

    /**
     * 未知用户占位符（nginx 日志惯例）；刻意用语义词而非短符号（如 "-"），避免与真实用户名撞车
     */
    private static final String UNKNOWN_USER = "unknown";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        RequestPath path = request.getPath();

        LoginUserInfo loginUser = exchange.getAttribute(LoginUserSnapshotFilter.LOGIN_USER_SNAPSHOT_KEY);
        String username = loginUser != null ? loginUser.getUsername() : UNKNOWN_USER;
        String realName = loginUser != null ? loginUser.getRealName() : UNKNOWN_USER;

        long start = System.nanoTime();
        return chain.filter(exchange).doFinally(signal -> {

            long costMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            log.info("【访问日志】时间:{}，用户:{}，姓名:{}，方法:{}，路径:{}，状态码:{}，耗时:{}ms",
                    LocalDateTime.now().format(Constant.DATE_TIME_FORMATTER), username, realName, method, path, getStatusCode(exchange), costMs);
        });
    }


    /**
     * 取响应状态码；请求异常中止（onError/cancel）时响应未写状态码，返回 "-"
     */
    private String getStatusCode(ServerWebExchange exchange) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        return status != null ? String.valueOf(status.value()) : "-";
    }


}
