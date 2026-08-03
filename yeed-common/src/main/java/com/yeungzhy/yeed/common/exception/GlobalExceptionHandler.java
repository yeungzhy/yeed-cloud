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

    /* ================================================= 数据库异常 ================================================
     * 安全原则：数据库异常的 message 通常含 SQL 语句、表名、字段名、约束名等敏感信息，
     * 严禁透传到前端（只返回通用文案）；日志只打印异常类型与完整堆栈用于排查，不把 message 拼进日志正文，
     * 避免敏感 SQL 随日志扩散到 ELK 等采集系统。
     */
    /** 唯一键冲突 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ApiResult<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.error("数据库唯一键冲突 [{}]：", e.getClass().getName(), e);
        return ApiResult.error(ApiResult.CommonCode.DUPLICATE_KEY_ERROR);
    }

    /** 数据完整性异常（如外键关联、非空字段为null） */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ApiResult<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        log.error("数据完整性异常 [{}]：", e.getClass().getName(), e);
        return ApiResult.error(ApiResult.CommonCode.DATA_INTEGRITY_ERROR);
    }

    /** Spring 数据访问总异常（兜底所有DAO层异常）, 包含：连接池耗尽、SQL语法错误、超时等 */
    @ExceptionHandler(DataAccessException.class)
    public ApiResult<Void> handleDataAccess(DataAccessException e) {
        log.error("数据访问异常 [{}]：", e.getClass().getName(), e);
        return ApiResult.error(ApiResult.CommonCode.DATABASE_ERROR);
    }

    /** 原生SQL异常（兜底） */
    @ExceptionHandler(SQLException.class)
    public ApiResult<Void> handleSQL(SQLException e) {
        log.error("原生SQL异常 [{}]：", e.getClass().getName(), e);
        return ApiResult.error(ApiResult.CommonCode.DATABASE_ERROR);
    }

    /** 空指针异常（属于系统BUG，必须记全栈，返回统一提示） */
    @ExceptionHandler(NullPointerException.class)
    public ApiResult<Void> handleNPE(NullPointerException e) {
        // 这里务必打印全栈，方便开发定位具体哪一行代码没判空
        log.error("发生空指针异常，请检查代码逻辑：", e);
        return ApiResult.error(ApiResult.CommonCode.SYSTEM_ERROR);
    }

    /**
     * 非法参数异常（常见于工具类校验、枚举转换失败）
     * 注意：不透传 e.getMessage()，因为工具类/框架内部抛出的 IAE 消息可能含内部字段名、
     * SQL 片段等敏感信息，统一返回通用参数错误提示。详细原因见日志。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResult<Void> handleIllegalArg(IllegalArgumentException e) {
        log.warn("非法参数异常 [{}]：", e.getClass().getName(), e);
        return ApiResult.error(ApiResult.CommonCode.PARAM_INVALID);
    }

    /** 非法状态异常（如 Spring 容器状态错误、重复初始化等） */
    @ExceptionHandler(IllegalStateException.class)
    public ApiResult<Void> handleIllegalState(IllegalStateException e) {
        log.error("系统状态异常：", e);
        return ApiResult.error(ApiResult.CommonCode.ILLEGAL_STATE_ERROR);
    }

    /* ========================================= 网络与远程调用异常 =========================================
     * 收敛为两类：
     * 1. RestClientException（Spring 远程调用总异常）—— 覆盖 ResourceAccessException /
     *    HttpClientErrorException / HttpServerErrorException，统一返回"外部服务调用失败"
     * 2. 原生网络异常（ConnectException / SocketTimeoutException）—— 返回"网络连接异常"
     * 两者均不透传 e.getMessage()，避免下游响应体或内部细节泄露给前端。
     */
    /**
     * RestClient / RestTemplate / Feign 远程调用异常总兜底
     * （含连接超时、读写超时、404/500 等下游响应错误）
     */
    @ExceptionHandler(org.springframework.web.client.RestClientException.class)
    public ApiResult<Void> handleRestClient(org.springframework.web.client.RestClientException e) {
        log.error("远程服务调用失败 [{}]：", e.getClass().getName(), e);
        return ApiResult.error(ApiResult.CommonCode.REMOTE_SERVICE_ERROR);
    }

    /**
     * 原生网络异常（DNS 解析失败、拒绝连接、Socket 超时）
     */
    @ExceptionHandler({java.net.ConnectException.class, java.net.SocketTimeoutException.class})
    public ApiResult<Void> handleNetwork(java.net.SocketTimeoutException e) {
        log.error("网络连接异常 [{}]：", e.getClass().getName(), e);
        return ApiResult.error(ApiResult.CommonCode.NETWORK_ERROR);
    }


    /* ================================================= 兜底异常 ================================================ */
    /** 兜底 */
    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleAll(Exception e) {
        log.error("系统未捕获异常：", e);
        return ApiResult.error();
    }

}
