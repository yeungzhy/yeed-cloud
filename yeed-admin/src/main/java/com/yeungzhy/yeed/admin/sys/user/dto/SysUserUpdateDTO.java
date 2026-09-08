package com.yeungzhy.yeed.admin.sys.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 系统用户 DTO（前端入参）
 *
 * @author yeungzhy
 * @since 2026-08-05 20:28:12
 */
@Data
@Accessors(chain = true)
public class SysUserUpdateDTO {

    /** 雪花ID主键 */
    @NotNull(message = "主键 ID 不能为空")
    private Long id;

    /** 系统登录名（登录入口，未删用户中唯一） */
    private String username;
    /** 当前密码（校验操作人身份，不是新密码；改密走 SysUserPasswordDTO） */
    private String password;
    /** 真实姓名（用于前台展示） */
    private String realName;
    /** 工号（登录入口，未删用户中唯一） */
    private String employeeNo;
    /** 手机号 */
    private String phone;
    /** 邮箱 */
    private String email;

}
