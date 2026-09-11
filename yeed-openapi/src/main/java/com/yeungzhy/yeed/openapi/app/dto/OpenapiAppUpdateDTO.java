package com.yeungzhy.yeed.openapi.app.dto;

import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * OpenApi 接入应用 DTO（前端入参）
 *
 * @author yeungzhy
 * @since 2026-09-09 20:27:24
 */
@Data
@Accessors(chain = true)
public class OpenapiAppUpdateDTO {

    // ================== 主键 ==================
    /** 雪花ID主键 */
    private Long id;

    // ================== 业务字段 ==================
    /** 应用标识(参与签名与AAD,未删记录中唯一) */
    private String appId;
    /** 应用名称 */
    private String appName;
    /** 当前主RSA公钥(Base64(DER),不含PEM头尾与换行) */
    private String publicKey;
    /** 上一把RSA公钥(轮换过渡期内仍接受验签,过期后由管理端置NULL) */
    private String publicKeyPrev;
    /** 上一把公钥失效时刻,到点即不再接受验签 */
    private LocalDateTime prevKeyExpireTime;
    /** 状态:0-停用,1-启用 */
    private EnableStatusEnum status;


}
