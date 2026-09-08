package com.yeungzhy.yeed.admin.sys.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 系统用户 修改密码 DTO（前端入参）
 *
 * <p>一个入参同时承载「自助改密」与「管理员重置他人密码」：
 * <ul>
 *   <li>{@code id} 为空 → 改当前登录用户自己的密码，必须校验 {@code oldPassword}；</li>
 *   <li>{@code id} 非空 → 管理员重置指定用户密码，不校验旧密码（能否调用由菜单权限控制）。</li>
 * </ul>
 * 二者判定发生在 Service 层，不以「前端传不传旧密码」为准，那等于把安全判定交给调用方
 *
 * @author yeungzhy
 * @since 2026-09-08
 */
@Data
@Accessors(chain = true)
public class SysUserPasswordDTO {

    /** 目标用户 ID；为空表示修改当前登录用户自己的密码 */
    private Long id;

    /** 原密码（改自己时必填） */
    private String oldPassword;

    /** 新密码（明文入参，Service 层 Argon2 散列后落库） */
    @NotBlank(message = "新密码不能为空")
    private String newPassword;

}
