package com.yeungzhy.yeed.common.exception;

import com.yeungzhy.yeed.common.result.ApiResult;
import lombok.Getter;

/**
 * 自定义业务异常, 仅允许传入错误码枚举 和 自定义描述
 *
 * @author yeungzhy
 */
@Getter
public class BizException extends RuntimeException {

    /** 错误码枚举 */
    private final ApiResult.CommonCode commonCode;
    /** 自定义描述（可选，若为null则使用 commonCode.desc） */
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
