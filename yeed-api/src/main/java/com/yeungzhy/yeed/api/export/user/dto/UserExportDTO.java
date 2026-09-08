package com.yeungzhy.yeed.api.export.user.dto;

import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import com.yeungzhy.yeed.common.core.sensitive.Sensitive;
import com.yeungzhy.yeed.common.core.sensitive.SensitiveType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 系统用户 导出 DTO
 *
 * <p>不导出 password 与 {@code *_bidx} 盲索引列：导出文件是明文，落这两列等于把密文和检索哈希一起外泄；
 * 手机号与邮箱只导出 {@code @Sensitive} 掩码后的值
 *
 * @author yeungzhy
 * @since 2026-08-13 06:48:01
 */
@Data
@Accessors(chain = true)
public class UserExportDTO {

    /** 雪花ID主键 */
    private Long id;

    /** 系统登录名 */
    private String username;
    /** 真实姓名（用于前台展示） */
    private String realName;
    /** 工号 */
    private String employeeNo;
    /** 手机号 */
    @Sensitive(type = SensitiveType.PHONE)
    private String phone;
    /** 邮箱 */
    @Sensitive(type = SensitiveType.EMAIL)
    private String email;
    /** 状态：0-禁用，1-启用 */
    private EnableStatusEnum status;
    /** 创建时间 */
    private LocalDateTime createTime;

}
