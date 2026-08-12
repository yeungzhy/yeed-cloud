package com.yeungzhy.yeed.admin.sys.role.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yeungzhy.yeed.common.data.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

/**
 * 角色菜单关联表
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:50
 */
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName(value = "yeed_sys_role_menu", autoResultMap = true)
public class SysRoleMenu extends BaseEntity {

    /** 角色ID */
    private Long roleId;
    /** 菜单ID */
    private Long menuId;

}
