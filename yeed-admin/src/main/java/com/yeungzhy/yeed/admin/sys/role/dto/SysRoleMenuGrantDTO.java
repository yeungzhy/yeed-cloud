package com.yeungzhy.yeed.admin.sys.role.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 角色菜单授权 入参 DTO
 *
 * <p>全量覆盖式授权：menuIds 是角色最终的完整权限集合（含按钮权限点），Service 先清空旧关联再批量写入，
 * 前端授权页提交整棵树选中的节点即可，不必自己补祖先
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:14
 */
@Data
@Accessors(chain = true)
public class SysRoleMenuGrantDTO {

    /** 角色ID */
    @NotNull(message = "角色ID不能为空")
    private Long roleId;

    /** 授权的菜单ID集合（全量覆盖，含按钮权限点） */
    @NotEmpty(message = "菜单ID集合不能为空")
    private List<Long> menuIds;

}
