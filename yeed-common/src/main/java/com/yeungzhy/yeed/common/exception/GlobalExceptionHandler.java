package com.yeungzhy.yeed.common.exception;

import com.yeungzhy.yeed.common.result.ApiResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;
import java.util.stream.Collectors;

/**
 * 统一异常处理
 *
 * @author yeungzhy
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /* ================================================= 业务异常 ================================================ */
    /** 业务异常 */
    @ExceptionHandler(BizException.class)
    public ApiResult<Void> handleBizException(BizException e) {
        return ApiResult.error(e.getMessage());
    }

    /* ================================================= 参数异常 ================================================ */
    /** Valid 校验失败（@RequestBody） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(","));
        return ApiResult.error(msg);
    }

    /** 普通参数绑定校验（@RequestParam 或 @ModelAttribute） */
    @ExceptionHandler(BindException.class)
    public ApiResult<Void> handleBind(BindException e) {
        String msg = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ApiResult.error(msg);
    }

    /** 单个参数校验失败（如 @RequestParam @NotBlank） */
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResult<Void> handleConstraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations()
                .stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return ApiResult.error(msg);
    }

    /** 请求体JSON格式错误（如缺少花括号、类型不匹配） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResult<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return ApiResult.error("请求参数格式错误，请检查JSON结构");
    }

    /** 方法参数类型不匹配（如String传给Long） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResult<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ApiResult.error("参数[" + e.getName() + "]类型错误");
    }

    /** 缺少必要请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResult<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return ApiResult.error("缺少必要参数：" + e.getParameterName());
    }

    /** 请求方法不支持（如公司禁用GET，如果误发GET会触发） */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResult<Void> handleMethodNotSupport(HttpRequestMethodNotSupportedException e) {
        return ApiResult.error("不支持的请求方法，请使用POST");
    }

    /* ================================================= 数据库异常 ================================================ */
    /** 唯一键冲突 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ApiResult<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.error("数据库唯一键冲突：{}", e.getMessage(), e);
        return ApiResult.error(ApiResult.CommonCode.DUPLICATE_KEY_ERROR);
    }

    /** 数据完整性异常（如外键关联、非空字段为null） */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ApiResult<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        log.error("数据完整性异常：{}", e.getMessage(), e);
        return ApiResult.error(ApiResult.CommonCode.DATA_INTEGRITY_ERROR);
    }

    /** Spring 数据访问总异常（兜底所有DAO层异常）, 包含：连接池耗尽、SQL语法错误、超时等 */
    @ExceptionHandler(DataAccessException.class)
    public ApiResult<Void> handleDataAccess(DataAccessException e) {
        log.error("数据访问异常（SQL/连接/事务）：{}", e.getMessage(), e);
        return ApiResult.error(ApiResult.CommonCode.DATABASE_ERROR);
    }

    /** 原生SQL异常（兜底） */
    @ExceptionHandler(SQLException.class)
    public ApiResult<Void> handleSQL(SQLException e) {
        log.error("原生SQL异常：{}", e.getMessage(), e);
        return ApiResult.error(ApiResult.CommonCode.DATABASE_ERROR);
    }

    /* ========================================= 网络与远程调用异常 ========================================= */
    /** 空指针异常（属于系统BUG，必须记全栈，返回统一提示） */
    @ExceptionHandler(NullPointerException.class)
    public ApiResult<Void> handleNPE(NullPointerException e) {
        // 这里务必打印全栈，方便开发定位具体哪一行代码没判空
        log.error("发生空指针异常，请检查代码逻辑：", e);
        return ApiResult.error(ApiResult.CommonCode.SYSTEM_ERROR);
    }

    /**
     * 非法参数异常（常见于工具类校验、枚举转换失败）
     * 如果异常消息是业务友好的，可以透传；否则兜底
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResult<Void> handleIllegalArg(IllegalArgumentException e) {
        log.warn("非法参数异常：{}", e.getMessage());
        // 如果消息已经是业务友好的（如"性别只能为0或1"），直接返回；否则用枚举
        String msg = e.getMessage();
        if (msg != null && !msg.isEmpty()) {
            return ApiResult.error(msg);
        }
        return ApiResult.error(ApiResult.CommonCode.PARAM_INVALID);
    }

    /** 非法状态异常（如 Spring 容器状态错误、重复初始化等） */
    @ExceptionHandler(IllegalStateException.class)
    public ApiResult<Void> handleIllegalState(IllegalStateException e) {
        log.error("系统状态异常：", e);
        return ApiResult.error(ApiResult.CommonCode.ILLEGAL_STATE_ERROR);
    }

    /* ========================================= 网络与远程调用异常 ========================================= */
    /**
     * RestTemplate / Feign 远程调用超时、连接重置等
     * ResourceAccessException 是 Spring 对网络超时的通用包装
     */
    @ExceptionHandler(org.springframework.web.client.ResourceAccessException.class)
    public ApiResult<Void> handleResourceAccess(org.springframework.web.client.ResourceAccessException e) {
        log.error("远程服务连接超时或网络不可达：{}", e.getMessage());
        return ApiResult.error(ApiResult.CommonCode.NETWORK_ERROR);
    }

    /**
     * 处理 HttpClient 异常（如 404, 500 等来自下游的响应）
     */
    @ExceptionHandler(org.springframework.web.client.HttpClientErrorException.class)
    public ApiResult<Void> handleHttpClientError(org.springframework.web.client.HttpClientErrorException e) {
        log.warn("下游服务返回客户端错误，状态码：{}，响应体：{}", e.getStatusCode(), e.getResponseBodyAsString());
        return ApiResult.error(ApiResult.CommonCode.REMOTE_SERVICE_ERROR);
    }

    @ExceptionHandler(org.springframework.web.client.HttpServerErrorException.class)
    public ApiResult<Void> handleHttpServerError(org.springframework.web.client.HttpServerErrorException e) {
        log.error("下游服务内部错误，状态码：{}，响应体：{}", e.getStatusCode(), e.getResponseBodyAsString());
        return ApiResult.error(ApiResult.CommonCode.REMOTE_SERVICE_ERROR);
    }

    /**
     * 原生网络连接异常（如 DNS 解析失败、拒绝连接）
     */
    @ExceptionHandler(java.net.ConnectException.class)
    public ApiResult<Void> handleConnect(java.net.ConnectException e) {
        log.error("网络连接失败：{}", e.getMessage());
        return ApiResult.error(ApiResult.CommonCode.NETWORK_ERROR);
    }

    /**
     * Socket 超时（读取超时或连接超时）
     */
    @ExceptionHandler(java.net.SocketTimeoutException.class)
    public ApiResult<Void> handleSocketTimeout(java.net.SocketTimeoutException e) {
        log.error("Socket 读写超时：{}", e.getMessage());
        return ApiResult.error(ApiResult.CommonCode.NETWORK_ERROR);
    }

    /* ========================================= Spring 其他常见Web异常 ========================================= */
    /** 不支持的媒体类型（如接口只接收 JSON，前端传了 XML） */
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ApiResult<Void> handleMediaType(org.springframework.web.HttpMediaTypeNotSupportedException e) {
        log.warn("不支持的媒体类型：{}", e.getContentType());
        return ApiResult.error("不支持的 Content-Type");
    }

    /**
     * 异步请求超时（如果使用了 @Async 或 DeferredResult）
     */
    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestTimeoutException.class)
    public ApiResult<Void> handleAsyncTimeout(org.springframework.web.context.request.async.AsyncRequestTimeoutException e) {
        log.warn("异步请求处理超时");
        return ApiResult.error("请求处理超时，请稍后重试");
    }

    /* ================================================= 兜底异常 ================================================ */
    /** 兜底 */
    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleAll(Exception e) {
        log.error("系统未捕获异常：", e);
        return ApiResult.error();
    }

}
