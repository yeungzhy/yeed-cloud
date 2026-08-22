package com.yeungzhy.yeed.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeungzhy.yeed.common.core.result.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关全局异常处理器（WebFlux reactive 版）
 *
 * <p>覆盖三类异常：
 * <ol>
 *   <li>Sa-Token 鉴权异常 —— 由 {@code SaReactorFilter.setError} 在 WebFilter 阶段自处理，
 *       不会传播到本处理器</li>
 *   <li>路由/下游异常 —— {@link NotFoundException}（Spring Cloud Gateway 无路由匹配）、
 *       {@link TimeoutException}（Spring Cloud Gateway 请求超时）、
 *       {@link ResponseStatusException}（下游 5xx / 连接拒绝 / 读写超时等）</li>
 *   <li>兜底 —— 其它未捕获 {@link Throwable}</li>
 * </ol>
 *
 * <p>网关层错误返回真实 HTTP 状态码 + ApiResult body（描述见 {@code body.msg}），前端按状态码分流：
 * 401 跳登录 / 403 提示无权限 / 404 资源不存在 / 502 下游不可用 / 504 请求超时 / 500 系统繁忙。
 * 下游服务自身的业务错误（参数校验、数据重复等）仍由下游以 HTTP 200 + body 业务码返回并经网关透传。
 * 分层原则：HTTP 状态码表达通用/稳定语义（网关层），业务码表达领域/多样语义（下游层），互不替代。
 *
 * <p>{@link Order} 设为 {@link Ordered#HIGHEST_PRECEDENCE}，覆盖 Spring 默认的
 * {@code DefaultErrorWebExceptionHandler}（其优先级为 {@code 0}），确保所有异常都走本处理器。
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalWebExceptionHandler implements WebExceptionHandler {

    /** 序列化失败时的兜底响应体，避免异常处理本身再抛异常导致响应无法写回 */
    private static final byte[] FALLBACK_BODY = String.format("{\"code\":%d,\"msg\":\"%s\",\"data\":null}",
            ApiResult.CommonCode.SYSTEM_ERROR.getCode(), ApiResult.CommonCode.SYSTEM_ERROR.getMsg()).getBytes(StandardCharsets.UTF_8);

    private final ObjectMapper objectMapper;

    public GlobalWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // 响应已提交（下游已写部分数据）则无法替换 body，只能传播异常交给底层处理
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        ResolvedError resolved = resolve(ex);

        exchange.getResponse().setStatusCode(resolved.status());
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(resolved.result());
        } catch (JsonProcessingException e) {
            bytes = FALLBACK_BODY;
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * 把异常映射为（响应体，HTTP 状态码），并记录相应级别的日志
     */
    private ResolvedError resolve(Throwable ex) {
        // Spring Cloud Gateway 路由不存在（org.springframework.cloud.gateway.support.NotFoundException）
        // body 用 CommonCode.NOT_FOUND（code=404），与下游 servlet 404 契约保持一致
        if (ex instanceof NotFoundException) {
            log.warn("网关路由不存在：{}", ex.getMessage());
            return new ResolvedError(ApiResult.error(ApiResult.CommonCode.NOT_FOUND), HttpStatus.NOT_FOUND);
        }

        // Spring Cloud Gateway 请求超时（org.springframework.cloud.gateway.support.TimeoutException）
        if (ex instanceof TimeoutException) {
            log.warn("网关请求超时：{}", ex.getMessage());
            return new ResolvedError(ApiResult.error("请求超时，请稍后重试"), HttpStatus.GATEWAY_TIMEOUT);
        }

        // 路由失败 / 下游 5xx / 连接拒绝 / 读写超时 等
        // 不打印下游响应体（ResponseStatusException.getMessage() 可能含下游 body），避免敏感信息泄露到日志
        if (ex instanceof ResponseStatusException rse) {
            HttpStatusCode status = rse.getStatusCode();
            // 404 资源不存在（无匹配路由 / 无静态资源，如浏览器自动探测 favicon.ico）：客户端错误，warn 级
            if (status == HttpStatus.NOT_FOUND) {
                log.warn("网关资源不存在 [{}]：{}", status, ex.getClass().getSimpleName());
                return new ResolvedError(ApiResult.error(ApiResult.CommonCode.NOT_FOUND), HttpStatus.NOT_FOUND);
            }
            log.error("网关路由失败 [{}]：{}", status, ex.getClass().getSimpleName());
            return new ResolvedError(ApiResult.error(ApiResult.CommonCode.REMOTE_SERVICE_ERROR), HttpStatus.BAD_GATEWAY);
        }

        // 兜底
        log.error("网关未捕获异常：", ex);
        return new ResolvedError(ApiResult.error(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 异常处理结果：响应体 + 对应 HTTP 状态码，两者由 {@code handle} 一起写回
     */
    private record ResolvedError(ApiResult<Void> result, HttpStatus status) { }
}
