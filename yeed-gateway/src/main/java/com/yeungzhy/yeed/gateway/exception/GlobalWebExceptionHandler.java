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
 * <p>统一返回 HTTP 200 + ApiResult body（业务码区分），与 Sa-Token {@code setError} 行为一致，
 * 前端只看 {@code body.code} 即可判断成功失败。
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
    private static final byte[] FALLBACK_BODY = String.format("{\"code\":%d,\"desc\":\"%s\",\"data\":null}",
            ApiResult.CommonCode.SYSTEM_ERROR.getCode(), ApiResult.CommonCode.SYSTEM_ERROR.getDesc()).getBytes(StandardCharsets.UTF_8);

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

        ApiResult<Void> result = resolve(ex);

        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(result);
        } catch (JsonProcessingException e) {
            bytes = FALLBACK_BODY;
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * 把异常映射为 ApiResult，并记录相应级别的日志
     */
    private ApiResult<Void> resolve(Throwable ex) {
        // Spring Cloud Gateway 路由不存在（org.springframework.cloud.gateway.support.NotFoundException）
        if (ex instanceof NotFoundException) {
            log.warn("网关路由不存在：{}", ex.getMessage());
            return ApiResult.error("请求的资源不存在");
        }

        // Spring Cloud Gateway 请求超时（org.springframework.cloud.gateway.support.TimeoutException）
        if (ex instanceof TimeoutException) {
            log.warn("网关请求超时：{}", ex.getMessage());
            return ApiResult.error("请求超时，请稍后重试");
        }

        // 路由失败 / 下游 5xx / 连接拒绝 / 读写超时 等
        // 不打印下游响应体（ResponseStatusException.getMessage() 可能含下游 body），避免敏感信息泄露到日志
        if (ex instanceof ResponseStatusException rse) {
            log.error("网关路由失败 [{}]：{}", rse.getStatusCode(), rse.getClass().getSimpleName());
            return ApiResult.error(ApiResult.CommonCode.REMOTE_SERVICE_ERROR);
        }

        // 兜底
        log.error("网关未捕获异常：", ex);
        return ApiResult.error();
    }
}
