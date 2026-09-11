package com.yeungzhy.yeed.openapi.app.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * OpenApi接入应用表 VO（返回出参）
 *
 * @author yeungzhy
 * @since 2026-09-09 20:27:24
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenapiAppVO {

    // ================== 主键 ==================
    /** 雪花ID主键 */
    private Long id;

    // ================== 业务字段 ==================
    /** 应用标识(系统生成,参与签名与AAD,未删记录中唯一) */
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
    /** 备注 */
    private String remark;

    // ================== 审计字段 ==================
    /** 创建人 */
    private Long createBy;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新人 */
    private Long updateBy;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 删除人 */
    private Long deleteBy;
    /** 逻辑删除,0-未删,时间戳-已删 */
    private Long deleteTime;
    /** 乐观锁 */
    private Integer version;
    /** 扩展信息 */
    private Map<String, Object> extra;

}
