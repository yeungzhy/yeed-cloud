package com.yeungzhy.yeed.common.web.exception;

import com.yeungzhy.yeed.common.core.exception.BizException;
import com.yeungzhy.yeed.common.core.result.ApiResult;
import com.yeungzhy.yeed.common.web.annotation.InternalApi;
import com.yeungzhy.yeed.common.web.config.ExceptionHandlerAutoConfiguration;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 内部 Feign 端点（标注 {@link InternalApi} 的 Controller）专用异常处理
 *
 * <p>与对外端点（HTTP 200 + 业务码）相反，内部端点的失败一律转为 HTTP 错误码
 * （body 仍为 ApiResult 结构），供消费方 Feign 的 ErrorDecoder 解析出 code/msg：
 * <ul>
 *   <li>{@link BizException}：HTTP 500，body 携带码（无码回落 SYSTEM_ERROR）与透传话术；</li>
 *   <li>参数/约束校验失败：HTTP 400 + {@code PARAM_INVALID}；</li>
 *   <li>请求体不可读（JSON 结构错误）、参数类型不匹配、缺参：HTTP 400 + {@code PARAM_INVALID}
 *       ——属调用方契约违约，必须落在 400 分支，否则会被 {@code Exception} 兜底误判成
 *       服务方 500 故障，告警与日志噪音都会指向错误的排查方向；</li>
 *   <li>其余未捕获异常：HTTP 500 + {@code SYSTEM_ERROR}（记全栈）。</li>
 * </ul>
 *
 * <p>为何用 {@code @Order(HIGHEST_PRECEDENCE)}：{@link ExternalApiExceptionHandler} 同样能处理
 * {@code BizException}，但会把内部端点错误包成 HTTP 200：裸数据 Feign 契约会把错误体
 * 反序列化成"字段全 null 的假成功"。本类排序更靠前，对内部 Controller 的异常优先接管。
 *
 * <p>装配方式：本类位于 {@code common-web}，不在任何业务模块的组件扫描边界内，
 * 由 {@link ExceptionHandlerAutoConfiguration} 显式注册为 Bean。
 *
 * @author yeungzhy
 * @see InternalApi
 * @see ExceptionHandlerAutoConfiguration
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(annotations = InternalApi.class)
public class InternalApiExceptionHandler {

    /**
     * 内部业务失败：HTTP 500 + 业务码透传（无码回落 SYSTEM_ERROR）。
     * 业务失败属契约内正常路径，不记 ERROR 日志。
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResult<Void>> handleBiz(BizException e) {
        ApiResult.CommonCode code = e.getCommonCode() == null ? ApiResult.CommonCode.SYSTEM_ERROR : e.getCommonCode();
        return ResponseEntity.internalServerError()
                .body(ApiResult.error(code, e.getMessage()));
    }

    /**
     * 内部参数/约束校验失败（Feign 调用方契约违约）：HTTP 400 + PARAM_INVALID
     *
     * <p>覆盖两类：字段约束不满足（{@code @Valid} / {@code @Validated}）与报文本身不可解析
     * （JSON 结构错误、类型不匹配、缺参）。均属调用方错误，不记 ERROR 日志。
     */
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiResult<Void>> handleValidation(Exception e) {
        return ResponseEntity.badRequest()
                .body(ApiResult.error(ApiResult.CommonCode.PARAM_INVALID));
    }

    /** 内部端点未捕获异常（系统 bug）：HTTP 500 + SYSTEM_ERROR，记全栈 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleAll(Exception e) {
        log.error("内部接口未捕获异常：", e);
        return ResponseEntity.internalServerError()
                .body(ApiResult.error(ApiResult.CommonCode.SYSTEM_ERROR));
    }

}
