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
 * <p>记录每个请求的时间、用户、姓名、方法、路径、状态码、耗时；耗时在 {@code doFinally} 统计，
 * 覆盖正常完成、异常终止、客户端取消三种信号，后两者没有状态码，记为 {@code -}
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

    /**
     * 记录一行访问日志
     *
     * <p>身份取自 {@link LoginUserSnapshotFilter} 的快照，取不到按匿名记
     *
     * @param exchange 当前请求上下文，不能为 null
     * @param chain    后续过滤链，不能为 null
     * @return 链路信号，本过滤器不改写
     */
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
     * 取响应状态码
     *
     * @param exchange 当前请求上下文，不能为 null
     * @return 状态码文本；异常中止（onError / cancel）时响应未写状态码，返回 {@code -}
     */
    private String getStatusCode(ServerWebExchange exchange) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        return status != null ? String.valueOf(status.value()) : "-";
    }


}
