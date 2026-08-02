package com.yeungzhy.yeed.common.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * 统一API响应结果封装，确保所有接口返回一致的JSON结构：
 * <pre>{@code { code: int, desc: String, data: T }}</pre>
 *
 * <p><b>核心设计</b>
 * <ul>
 *   <li><b>强制枚举错误码</b> – 所有业务状态码定义在 {@link CommonCode} 枚举中，禁止直接传入int值，避免项目内错误码散乱。</li>
 *   <li><b>静态工厂方法</b> – 仅通过 {@code ok()} 和 {@code error(...)} 系列方法构建，语义清晰，符合现代编程习惯。</li>
 *   <li><b>状态码解耦</b> – 业务code（0成功，1000+失败）与HTTP状态码完全分离，前端可统一处理业务逻辑。</li>
 * </ul>
 *
 * @author yeungzhy
 */
@Getter
@Setter(AccessLevel.PRIVATE)
@ToString
@Accessors(chain = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResult<T> {

    /**
     * 业务状态码
     * <p>0 表示成功，非 0 表示失败。具体定义见 {@link CommonCode}
     * <p>只能通过 CommonCode 枚举定义，禁止外部随意传入
     */
    private int code;

    /**
     * 响应描述信息
     * <p>成功时通常为 "成功"，失败时为具体错误原因或提示文案
     */
    private String desc;

    /** 响应业务数据 */
    private T data;


    public static <T> ApiResult<T> ok() {
        return ok(CommonCode.OK.desc, null);
    }

    public static <T> ApiResult<T> ok(T response) {
        return ok(CommonCode.OK.desc, response);
    }

    public static <T> ApiResult<T> ok(String desc, T response) {
        return build(CommonCode.OK, desc, response);
    }

    public static <T> ApiResult<T> error() {
        return error(CommonCode.SYSTEM_ERROR, null);
    }

    public static <T> ApiResult<T> error(String desc) {
        return build(CommonCode.SYSTEM_ERROR, desc, null);
    }

    public static <T> ApiResult<T> error(CommonCode commonCode) {
        return error(commonCode, null);
    }

    public static <T> ApiResult<T> error(CommonCode commonCode, T response) {
        return build(commonCode, commonCode.desc, response);
    }


    /**
     * 唯一的内部构建入口，强制传入 CommonCode
     * <p> 杜绝 int code 参数，防止内部代码出现“裸奔”的数字
     */
    private static <T> ApiResult<T> build(CommonCode commonCode, String desc, T response) {
        return new ApiResult<T>()
                .setCode(commonCode.code)
                .setDesc(desc)
                .setData(response);
    }


    /**
     * 判断结果是否成功
     *
     * @return true-成功，false-失败
     */
    @JsonIgnore
    public boolean isOk() {
        return this.code == CommonCode.OK.code;
    }

    /**
     * 判断结果是否失败
     *
     * @return true-失败，false-成功
     */
    @JsonIgnore
    public boolean isError() {
        return this.code != CommonCode.OK.code;
    }


    /**
     * 通用业务状态码枚举
     * <p> 所有错误码集中在 1000~9999 区间，与 HTTP 状态码彻底解耦
     */
    @Getter
    @AllArgsConstructor
    public enum CommonCode {

        /** 0 表示成功，不抢占 HTTP 语义 */
        OK(0, "成功"),
        /** 系统繁忙，请稍后重试 */
        SYSTEM_ERROR(1000, "系统繁忙，请稍后重试"),


        /** 参数校验失败 */
        PARAM_INVALID(1001, "参数校验失败"),
        /** 未登录或登录已过期 */
        UNAUTHORIZED(1002, "未登录或登录已过期"),
        /** 无操作权限 */
        FORBIDDEN(1003, "无操作权限"),



        DATABASE_ERROR(1004, "数据库操作异常，请稍后重试"),
        DUPLICATE_KEY_ERROR(1005, "数据已存在，请勿重复提交"),
        DATA_INTEGRITY_ERROR(1006, "数据关联异常，无法执行当前操作"),

        NETWORK_ERROR(1007, "网络连接异常，请检查网络后重试"),
        REMOTE_SERVICE_ERROR(1008, "外部服务调用失败，请稍后重试"),
        ILLEGAL_STATE_ERROR(1009, "系统状态异常，请刷新后重试"),


        ; // 所有错误码集中在 1000~9999 区间，与 HTTP 状态码彻底解耦

        private final int code;
        private final String desc;
    }

}
