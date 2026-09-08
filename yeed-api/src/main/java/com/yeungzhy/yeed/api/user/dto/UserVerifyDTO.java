package com.yeungzhy.yeed.api.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 凭据校验入参（auth → admin 内部 Feign 调用）
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@Data
@Accessors(chain = true)
public class UserVerifyDTO {

    /** 账号：系统登录名 username / 工号 employeeNo，两者共用同一登录入口 */
    @NotBlank(message = "账号不能为空")
    private String account;

    /** 密码明文，不能为空白；跨服务传输依赖内网 + 网关不外露 {@code /internal/**} */
    @NotBlank(message = "密码不能为空")
    private String password;

}
