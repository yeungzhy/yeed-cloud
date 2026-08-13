package com.yeungzhy.yeed.admin.sys.user.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 用户角色授权 入参 DTO
 *
 * <p>全量覆盖式授权：roleIds 为该用户最终的完整角色集合，
 * Service 先清空该用户旧关联再批量写入新关联，前端授权页提交完整勾选集合即可。
 *
 * @author yeungzhy
 * @since 2026-08-13 06:48:01
 */
@Data
@Accessors(chain = true)
public class SysUserRoleGrantDTO {

    /** 用户ID */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 授权的角色ID集合（全量覆盖） */
    @NotEmpty(message = "角色ID集合不能为空")
    private List<Long> roleIds;

}
