package com.yeungzhy.yeed.common.core.result;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

/**
 * 统一API响应结果封装，确保所有接口返回一致的JSON结构：
 * <pre>{@code { code: int, msg: String, data: T }}</pre>
 *
 * <p><b>核心设计</b>
 * <ul>
 *   <li><b>强制枚举错误码</b> – 所有业务状态码定义在 {@link CommonCode} 枚举中，禁止直接传入 int 值，避免项目内错误码散乱。</li>
 *   <li><b>静态工厂方法</b> – 仅通过 {@code ok()} 和 {@code error(...)} 系列方法构建，语义清晰，符合现代编程习惯。</li>
 *   <li><b>与 HTTP 状态码分工</b> – HTTP 状态码表达通用/稳定语义（网关层 401/403/404/5xx），
 *       body 业务码表达统一结构 + 领域语义（下游 200 + 业务码透传），两者互不替代：
 *       成功固定 0（不抢占 HTTP 200 语义），业务失败 1000+；
 *       {@code CommonCode} 中的 401/403/404 仅为网关鉴权错误与 404 场景的 HTTP 语义映射。</li>
 * </ul>
 *
 * @author yeungzhy
 */
@Getter
@Setter(AccessLevel.PRIVATE)
@ToString
@FieldNameConstants
@Accessors(chain = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResult<T> {

    /**
     * 业务状态码
     * <p>0 成功；401/403/404 为 HTTP 语义映射（见 {@link CommonCode}）；1000+ 业务失败。
     * <p>只能通过 CommonCode 枚举定义，禁止外部随意传入
     */
    private int code;

    /**
     * 响应描述信息
     * <p>成功时通常为 "成功"，失败时为具体错误原因或提示文案
     */
    private String msg;

    /** 响应业务数据 */
    private T data;


    public static <T> ApiResult<T> ok() {
        return ok(CommonCode.OK.msg, null);
    }

    public static <T> ApiResult<T> ok(T response) {
        return ok(CommonCode.OK.msg, response);
    }

    public static <T> ApiResult<T> ok(String msg, T response) {
        return build(CommonCode.OK, msg, response);
    }

    public static <T> ApiResult<T> error() {
        return error(CommonCode.SYSTEM_ERROR, null);
    }

    public static <T> ApiResult<T> error(String msg) {
        return build(CommonCode.SYSTEM_ERROR, msg, null);
    }

    public static <T> ApiResult<T> error(CommonCode commonCode) {
        return error(commonCode, null);
    }

    public static <T> ApiResult<T> error(CommonCode commonCode, T response) {
        return build(commonCode, commonCode.msg, response);
    }


    /**
     * 唯一的内部构建入口，强制传入 CommonCode
     * <p> 杜绝 int code 参数，防止内部代码出现“裸奔”的数字
     */
    private static <T> ApiResult<T> build(CommonCode commonCode, String msg, T response) {
        return new ApiResult<T>()
                .setCode(commonCode.code)
                .setMsg(msg)
                .setData(response);
    }


    /**
     * 判断结果是否成功
     *
     * @return true-成功，false-失败
     */
    public boolean succeed() {
        return this.code == CommonCode.OK.code;
    }

    /**
     * 判断结果是否失败
     *
     * @return true-失败，false-成功
     */
    public boolean failed() {
        return this.code != CommonCode.OK.code;
    }


    /**
     * 通用业务状态码枚举
     *
     * <p>按「HTTP 状态码表达通用/稳定语义、body 业务码表达统一结构 + 领域语义」的分工，
     * 分为三组（互不替代）：
     * <ul>
     *   <li><b>成功</b> – {@code OK(0)}：业务成功固定为 0，不与 HTTP 200 抢占语义；
     *       前端判成功统一看 {@code code == 0}（配合 HTTP 200）。</li>
     *   <li><b>HTTP 语义组</b> – {@code UNAUTHORIZED / FORBIDDEN / NOT_FOUND}：值对齐
     *       HTTP 状态码（401/403/404），仅用于网关鉴权错误与静态资源 404 等「无领域语义」
     *       场景，使 body 状态码与 HTTP 状态码一致，前端一次读懂。</li>
     *   <li><b>领域语义组</b> – {@code 1000~9999}：业务错误（参数/数据库/网络/远程调用等），
     *       由下游服务以 HTTP 200 透传，前端按 code 分流处理。</li>
     * </ul>
     */
    @Getter
    @AllArgsConstructor
    public enum CommonCode {

        /** 未登录或登录已过期 */
        UNAUTHORIZED(401, "未登录或登录已过期"),
        /** 无操作权限 */
        FORBIDDEN(403, "无操作权限"),
        /** 请求的资源不存在 */
        NOT_FOUND(404, "请求的资源不存在"),

        /** 成功：业务成功固定为 0，不抢占 HTTP 200 语义 */
        OK(0, "成功"),

        /** 系统繁忙，请稍后重试 */
        SYSTEM_ERROR(1000, "系统繁忙，请稍后重试"),
        /** 参数校验失败 */
        PARAM_INVALID(1001, "参数校验失败"),

        /** 数据库操作异常，请稍后重试 */
        DATABASE_ERROR(1004, "数据库操作异常，请稍后重试"),
        /** 数据已存在，请勿重复提交 */
        DUPLICATE_KEY_ERROR(1005, "数据已存在，请勿重复提交"),
        /** 数据关联异常，无法执行当前操作 */
        DATA_INTEGRITY_ERROR(1006, "数据关联异常，无法执行当前操作"),

        /** 网络连接异常，请检查网络后重试 */
        NETWORK_ERROR(1007, "网络连接异常，请检查网络后重试"),
        /** 外部服务调用失败，请稍后重试 */
        REMOTE_SERVICE_ERROR(1008, "外部服务调用失败，请稍后重试"),
        /** 系统状态异常，请刷新后重试 */
        ILLEGAL_STATE_ERROR(1009, "系统状态异常，请刷新后重试"),

        ; // 领域语义组集中在 1000~9999，与 HTTP 状态码彻底解耦

        private final int code;
        private final String msg;
    }

}
