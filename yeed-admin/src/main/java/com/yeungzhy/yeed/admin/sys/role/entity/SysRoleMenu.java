package com.yeungzhy.yeed.admin.sys.role.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 角色菜单关联表
 *
 * @author yeungzhy
 * @since 2026-08-09 10:25:49
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@TableName(value = "yeed_sys_role_menu")
public class SysRoleMenu {

    /** 雪花ID主键 */
    private Long id;
    /** 角色ID */
    private Long roleId;
    /** 菜单ID */
    private Long menuId;

}
