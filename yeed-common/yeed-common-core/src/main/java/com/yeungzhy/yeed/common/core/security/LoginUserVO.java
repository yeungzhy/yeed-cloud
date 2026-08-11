package com.yeungzhy.yeed.common.core.security;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 登录身份包
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@Data
@Accessors(chain = true)
public class LoginUserVO {

    /** 用户主键 */
    private Long userId;
    /** 真实姓名（前台展示） */
    private String realName;
    /** 系统登录名 */
    private String username;
    /** 工号 */
    private String employeeNo;
    /** 角色编码集合 */
    private List<String> roleCodes;
    /** 权限标识集合 */
    private List<String> perms;

}
