package com.yeungzhy.yeed.gateway.filter;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import com.yeungzhy.yeed.common.core.constant.Constant;
import com.yeungzhy.yeed.common.core.security.LoginUserHelper;
import com.yeungzhy.yeed.common.core.security.LoginUserInfo;
import com.yeungzhy.yeed.gateway.config.WebFilterConfig;
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
 * <p>记录每个请求：时间、用户、姓名、方法、路径、状态码、耗时。
 *
 * <p>以 {@link WebFilter} 形式由 {@link WebFilterConfig} 注册为最高优先级，早于
 * SaReactorFilter（源码 {@code @Order(-100)}）执行；Sa-Token 鉴权拒绝的请求
 * （401/403）同样会在 {@code doFinally} 中被记录，实现「所有请求全量入日志」。
 *
 * <p>耗时在 {@code doFinally} 中统计，覆盖整个下游转发 + 响应阶段。
 * 请求的真实结果以<b>状态码</b>为准：网关层错误（鉴权 401/403、资源不存在 404、
 * 下游不可用 502/504、兜底 500）返回真实 HTTP 状态码；下游服务业务错误仍透传
 * HTTP 200 + body 业务码，需看响应体 code 区分。
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

        LoginUserInfo loginUser = getLoginUser(exchange);
        String username = loginUser == null ? UNKNOWN_USER : loginUser.getUsername();
        String realName = loginUser == null ? UNKNOWN_USER : loginUser.getRealName();

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


    /**
     * 取当前会话登录用户；Sa-Token 上下文与 WebFlux 线程模型不亲和，
     * 需手动绑定/清理 SaReactorSyncHolder。未登录直接短路返回 null，
     * 避免匿名请求（放行名单等）触发底层 ERROR 堆栈噪音；读失败按匿名处理，不阻断请求
     */
    private LoginUserInfo getLoginUser(ServerWebExchange exchange) {
        try {
            SaReactorSyncHolder.setContext(exchange);
            if (!StpUtil.isLogin()) {
                return null;
            }
            return LoginUserHelper.getLoginUser();
        } catch (Exception e) {
            return null;
        } finally {
            SaReactorSyncHolder.clearContext();
        }
    }

}
