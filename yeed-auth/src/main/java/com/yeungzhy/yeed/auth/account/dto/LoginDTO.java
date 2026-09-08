package com.yeungzhy.yeed.auth.account.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 登录请求入参（前端 → auth）
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@Data
@Accessors(chain = true)
public class LoginDTO {

    /** 账号：系统登录名 username / 工号 employeeNo，两者共用同一登录入口 */
    @NotBlank(message = "账号不能为空")
    private String account;

    /** 密码明文，不能为空白；只在本次请求的 HTTPS 报文内出现，不做本地落库 */
    @NotBlank(message = "密码不能为空")
    private String password;

}
