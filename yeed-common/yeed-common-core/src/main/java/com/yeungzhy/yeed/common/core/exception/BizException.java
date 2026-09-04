package com.yeungzhy.yeed.common.core.exception;

import com.yeungzhy.yeed.common.core.result.ApiResult;
import lombok.Getter;

/**
 * 自定义业务异常
 *
 * <p>可仅带码（用 {@link ApiResult.CommonCode} 默认文案）
 * <p>仅带自定义消息（码回落为 {@code SYSTEM_ERROR}）
 * <p>或两者都带（如远程调用失败时透传下游业务话术 + 指定语义码）
 *
 * <p>消息是否透传前端由端点面向谁决定：对外端点（{@code ExternalApiExceptionHandler}）落在
 * HTTP 200 的 body 里，内部端点（{@code InternalApiExceptionHandler}）落在 HTTP 错误码的 body 里，
 * 两侧均原样透传 message；业务断言（{@code BizAssert}）应优先于手动抛出
 *
 * @author yeungzhy
 */
@Getter
public class BizException extends RuntimeException {

    /** 错误码枚举；null 表示未指定，由异常处理器回落 SYSTEM_ERROR */
    private final ApiResult.CommonCode commonCode;
    /** 自定义描述（可选，为 null 时使用 commonCode 默认文案） */
    private final String customMsg;

    public BizException(ApiResult.CommonCode commonCode) {
        this(commonCode, null);
    }

    public BizException(String customMsg) {
        this(null, customMsg);
    }

    public BizException(ApiResult.CommonCode commonCode, String customMsg) {
        // 最终文案 = customMsg 优先，其次 commonCode 默认文案，双空时兜底系统繁忙
        super(customMsg != null ? customMsg
                : (commonCode != null ? commonCode.getMsg() : ApiResult.CommonCode.SYSTEM_ERROR.getMsg()));
        this.commonCode = commonCode;
        this.customMsg = customMsg;
    }

}
