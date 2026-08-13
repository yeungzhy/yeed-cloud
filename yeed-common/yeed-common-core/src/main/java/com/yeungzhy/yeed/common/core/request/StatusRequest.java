package com.yeungzhy.yeed.common.core.request;

import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * 按唯一标识更新目标状态
 *
 * @author yeungzhy
 * @since 2026-08-13
 */
@Getter
public class StatusRequest extends IdRequest {

    /** 目标状态: 0=禁用 1=启用 */
    @NotNull(message = "目标状态不能为空, 0=禁用 1=启用")
    private EnableStatusEnum status;

}
