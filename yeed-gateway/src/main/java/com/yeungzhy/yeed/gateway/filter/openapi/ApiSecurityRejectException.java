package com.yeungzhy.yeed.gateway.filter.openapi;

import org.springframework.http.HttpStatus;

/**
 * OpenApi 明文协议错误信号（HTTP 状态码取真值，body 取 4xx1xx 业务码）
 *
 * <p>由各校验步骤抛出，最终由 {@code ApiSecurityFilter} 统一落明文响应，
 * 中间步骤不各自写响应，避免响应被写两次
 *
 * @author yeungzhy
 * @since 2026-09-10
 */
public class ApiSecurityRejectException extends RuntimeException {

    /** HTTP 状态码 */
    private final HttpStatus status;

    /** body 业务码 */
    private final int code;

    /** body 错误消息 */
    private final String msg;

    /**
     * 明文协议错误信号
     *
     * @param status HTTP 状态码
     * @param code body 业务码
     * @param msg body 错误消息
     */
    public ApiSecurityRejectException(HttpStatus status, int code, String msg) {
        super(msg);
        this.status = status;
        this.code = code;
        this.msg = msg;
    }

    /**
     * HTTP 状态码
     *
     * @return 状态码
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * body 业务码
     *
     * @return 业务码
     */
    public int code() {
        return code;
    }

    /**
     * body 错误消息
     *
     * @return 错误消息
     */
    public String msg() {
        return msg;
    }
}
