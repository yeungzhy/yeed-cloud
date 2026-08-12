package com.yeungzhy.yeed.common.core.exception;

import com.yeungzhy.yeed.common.core.result.ApiResult;
import lombok.Getter;

/**
 * 自定义业务异常
 *
 * <p>仅允许传入错误码枚举（{@link ApiResult.CommonCode}）或自定义描述；message 最终由
 * {@code GlobalExceptionHandler} 原样透传给前端用户。业务断言（{@code BizAssert}）应优先于手动抛出。
 *
 * @author yeungzhy
 */
@Getter
public class BizException extends RuntimeException {

    /** 错误码枚举 */
    private final ApiResult.CommonCode commonCode;
    /** 自定义描述（可选，若为 null 则使用 commonCode.desc） */
    private final String customDesc;

    public BizException(ApiResult.CommonCode commonCode) {
        this(commonCode, null, null);
    }

    public BizException(String customDesc) {
        this(null, customDesc, null);
    }

    private BizException(ApiResult.CommonCode commonCode, String customDesc, Throwable cause) {
        // 将最终的错误描述传给父类（用于堆栈追踪和日志）
        super(customDesc != null ? customDesc : commonCode.getDesc(), cause);
        this.commonCode = commonCode;
        this.customDesc = customDesc;
    }

}
