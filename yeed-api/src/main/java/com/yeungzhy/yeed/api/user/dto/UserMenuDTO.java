package com.yeungzhy.yeed.api.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 用户菜单树查询入参（auth → admin 内部 Feign 调用）
 *
 * @author yeungzhy
 * @since 2026-08-15
 */
@Data
@Accessors(chain = true)
public class UserMenuDTO {

    /** 用户主键 */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

}
