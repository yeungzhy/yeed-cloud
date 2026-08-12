package com.yeungzhy.yeed.admin.sys.user.dto;

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
public class SysUserAddDTO {

    /** 用户名 */
    private String username;
    /** 密码 */
    private String password;
    /** 邮箱 */
    private String email;

}
