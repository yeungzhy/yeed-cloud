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

    /** 用户主键，不能为 null；超管同样按 id 查，不走特殊分支 */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

}
