package com.yeungzhy.yeed.admin.sys.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yeungzhy.yeed.common.core.crypto.Crypto;
import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import com.yeungzhy.yeed.common.data.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 系统用户
 *
 * @author yeungzhy
 * @since 2026-08-13 06:48:01
 */
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName(value = "yeed_sys_user", autoResultMap = true)
public class SysUser extends BaseEntity {

    /** 系统登录名（登录入口，未删用户中唯一） */
    private String username;
    /**
     * 密码（Argon2id 单向散列）
     *
     * <p>不标 {@link Crypto}：散列本身不可逆，再套一层可逆加密只增加列长与解密依赖，
     * 出参侧靠 VO 不暴露该字段兜底
     */
    private String password;
    /** 真实姓名（用于前台展示） */
    private String realName;
    /** 工号（登录入口，未删用户中唯一） */
    private String employeeNo;
    /** 手机号 */
    @Crypto
    private String phone;
    /** 手机号盲索引 */
    private String phoneBidx;
    /** 邮箱 */
    @Crypto
    private String email;
    /** 邮箱盲索引 */
    private String emailBidx;
    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;
    /** 状态：0-禁用，1-启用 */
    private EnableStatusEnum status;


    /**
     * 邮箱归一化：大小写与首尾空白不改变邮箱身份，必须先归一再生成盲索引
     * <p>否则 {@code A@b.com} 与 {@code a@b.com} 会算出两个不同 bidx，唯一索引形同虚设
     */
    public static String normalizeEmail(String email) {
        String trimmed = StringUtils.trimToNull(email);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }


}
