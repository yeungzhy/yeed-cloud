package com.yeungzhy.yeed.admin.sys.user.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 系统用户 新增入参
 *
 * <p>只承载业务字段：主键由雪花生成、审计字段由框架自动填充，前端伪造不了
 *
 * @author yeungzhy
 * @since 2026-08-05 20:28:12
 */
@Data
@Accessors(chain = true)
public class SysUserAddDTO {

    /** 系统登录名（登录入口，未删用户中唯一） */
    private String username;
    /** 密码（明文入参，Service 层 Argon2 散列后落库） */
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
