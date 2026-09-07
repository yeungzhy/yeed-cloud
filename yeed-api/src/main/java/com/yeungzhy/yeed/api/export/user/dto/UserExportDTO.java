package com.yeungzhy.yeed.api.export.user.dto;

import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import com.yeungzhy.yeed.common.core.sensitive.Sensitive;
import com.yeungzhy.yeed.common.core.sensitive.SensitiveType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 系统用户 DTO（前端入参）
 *
 * @author yeungzhy
 * @since 2026-08-13 06:48:01
 */
@Data
@Accessors(chain = true)
public class UserExportDTO {

    /** 雪花ID主键 */
    private Long id;

    /** 真实姓名(用于前台展示) */
    private String realName;
    /** 系统登录名 */
    private String username;
    /** 工号 */
    private String employeeNo;
    /** 邮箱 */
    @Sensitive(type = SensitiveType.EMAIL)
    private String email;
    /** 状态：0-禁用，1-启用 */
    private EnableStatusEnum status;
    /** 创建时间 */
    private LocalDateTime createTime;

}
