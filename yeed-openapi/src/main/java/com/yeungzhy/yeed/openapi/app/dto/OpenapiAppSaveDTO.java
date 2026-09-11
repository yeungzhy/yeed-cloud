package com.yeungzhy.yeed.openapi.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * OpenApi 接入应用 DTO（前端入参）
 *
 * @author yeungzhy
 * @since 2026-09-09 20:27:24
 */
@Data
@Accessors(chain = true)
public class OpenapiAppSaveDTO {

    // ================== 业务字段 ==================
    /** 应用标识(参与签名与AAD,未删记录中唯一) */
    @NotBlank(message = "应用标识不能为空")
    private String appId;
    /** 应用名称 */
    @NotBlank(message = "应用名称不能为空")
    private String appName;

}
