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
 * 网关全局异常处理器（WebFlux）
 *
 * <p>按异常类型映射 HTTP 状态码：
 * <ul>
 *   <li>{@link NotFoundException}：无匹配路由为 404，下游无可用实例为 503
 *   <li>{@link TimeoutException}：请求超时为 504
 *   <li>{@link ResponseStatusException}：404 为资源不存在，其余按下游故障返回 502
 *   <li>其它 {@link Throwable}：兜底 500
 * </ul>
 *
 * <p>响应体统一为 {@link ApiResult}，真实语义写在 {@code body.msg}；网关只返回真实 HTTP 码，
 * 下游业务错误（参数校验、数据重复等）以 HTTP 200 + body 业务码原样透传，本处理器不改写
 *
 * <p>Sa-Token 鉴权异常由 {@code SaReactorFilter.setError} 在 WebFilter 阶段自处理，不传播到本处理器
 *
 * <p>{@link Order} 取 {@link Ordered#HIGHEST_PRECEDENCE}，用于覆盖优先级为 {@code 0} 的
 * {@code DefaultErrorWebExceptionHandler}，否则部分异常会走 Spring 默认处理器
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalWebExceptionHandler implements WebExceptionHandler {

    /** 序列化失败时的兜底响应体，避免异常处理自身再抛异常导致响应无法写回 */
    private static final byte[] FALLBACK_BODY = """
            {"code":%d,"msg":"%s","data":null}"""
            .formatted(ApiResult.CommonCode.SYSTEM_ERROR.getCode(), ApiResult.CommonCode.SYSTEM_ERROR.getMsg()).getBytes(StandardCharsets.UTF_8);

    private final ObjectMapper objectMapper;

    public GlobalWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 把异常写成 JSON 错误响应
     *
     * <p>响应已提交（下游已写出部分数据）时无法再替换 body，只能把异常传播给底层处理
     *
     * @param exchange 当前请求上下文
     * @param ex       待处理的异常
     * @return 写完响应即完成的信号，响应已提交时为 {@code Mono.error(ex)}
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
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
     * 把异常映射为响应体与 HTTP 状态码，并按类型记相应级别的日志
     *
     * @param ex 待映射的异常
     * @return 映射结果，恒不为 null
     */
    private ResolvedError resolve(Throwable ex) {
        // NotFoundException 是 ResponseStatusException 子类，必须先于它判定
        // 404 = 无匹配路由；503 = 下游无可用实例，差异只体现在 status 上
        if (ex instanceof NotFoundException nfe) {
            if (nfe.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("网关路由不存在：{}", nfe.getMessage());
                return new ResolvedError(ApiResult.error(ApiResult.CommonCode.NOT_FOUND), HttpStatus.NOT_FOUND);
            }
            log.warn("下游服务暂不可用 [{}]：{}", nfe.getStatusCode(), nfe.getMessage());
            return new ResolvedError(ApiResult.error(), HttpStatus.SERVICE_UNAVAILABLE);
        }

        // gateway.support 包下的超时异常，与 JUC 的同名类无关
        if (ex instanceof TimeoutException) {
            log.warn("网关请求超时：{}", ex.getMessage());
            return new ResolvedError(ApiResult.error("请求超时，请稍后重试"), HttpStatus.GATEWAY_TIMEOUT);
        }

        // 下游 5xx / 连接拒绝 / 读写超时
        // 不打印 getMessage()，其可能携带下游响应体，避免敏感信息落入日志
        if (ex instanceof ResponseStatusException rse) {
            HttpStatusCode status = rse.getStatusCode();
            // 404 多为浏览器探测 favicon.ico 之类的静态资源，非故障，故仅 warn
            if (status == HttpStatus.NOT_FOUND) {
                log.warn("网关资源不存在 [{}]：{}", status, ex.getClass().getSimpleName());
                return new ResolvedError(ApiResult.error(ApiResult.CommonCode.NOT_FOUND), HttpStatus.NOT_FOUND);
            }
            log.error("网关路由失败 [{}]：{}", status, ex.getClass().getSimpleName());
            return new ResolvedError(ApiResult.error(ApiResult.CommonCode.REMOTE_SERVICE_ERROR), HttpStatus.BAD_GATEWAY);
        }

        log.error("网关未捕获异常：", ex);
        return new ResolvedError(ApiResult.error(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** 异常处理结果：响应体与对应 HTTP 状态码，由 {@code handle} 一并写回 */
    private record ResolvedError(ApiResult<Void> result, HttpStatus status) { }
}
