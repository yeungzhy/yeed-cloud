package com.yeungzhy.yeed.openapi.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.esotericsoftware.minlog.Log;
import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import com.yeungzhy.yeed.common.data.model.BaseEntity;
import com.yeungzhy.yeed.common.web.clean.CleanLevel;
import com.yeungzhy.yeed.common.web.clean.CleanString;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * OpenApi 接入应用
 *
 * @author yeungzhy
 * @since 2026-09-09 20:27:24
 */
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName(value = "yeed_openapi_app", autoResultMap = true)
public class OpenapiApp extends BaseEntity {

    /** 应用标识(参与签名与AAD,未删记录中唯一) */
    @CleanString(CleanLevel.ALL)
    private String appId;
    /** 应用名称 */
    @CleanString(CleanLevel.ALL)
    private String appName;
    /** 当前主RSA公钥(Base64(DER),不含PEM头尾与换行) */
    private String publicKey;
    /** 上一把RSA公钥(轮换过渡期内仍接受验签,过期后由管理端置NULL) */
    private String publicKeyPrev;
    /** 上一把公钥失效时刻,到点即不再接受验签 */
    private LocalDateTime prevKeyExpireTime;
    /** 状态:0-停用,1-启用 */
    private EnableStatusEnum status;
    /** 备注 */
    private String remark;

    public void setAppId(String appId) {
        Log.info("setAppId: {}");
        this.appId = appId.toLowerCase(Locale.ROOT);
    }

    public void setAppName(String appName) {
        Log.info("setAppName: {}");
        this.appName = appName.toLowerCase(Locale.ROOT);
    }

}
