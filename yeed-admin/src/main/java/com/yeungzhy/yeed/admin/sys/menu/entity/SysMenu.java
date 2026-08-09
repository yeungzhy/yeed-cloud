package com.yeungzhy.yeed.admin.sys.menu.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yeungzhy.yeed.common.model.BaseEntity;

import lombok.experimental.Accessors;
import lombok.EqualsAndHashCode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 系统菜单权限表
 *
 * @author yeungzhy
 * @since 2026-08-09 10:26:00
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName(value = "yeed_sys_menu", autoResultMap = true)
public class SysMenu extends BaseEntity {

    /** 父菜单ID，0-顶级菜单 */
    private Long parentId;
    /** 菜单名称 */
    private String menuName;
    /** 菜单类型：1-目录，2-菜单，3-按钮 */
    private Integer menuType;
    /** 路由地址（目录/菜单页面对应前端路由，按钮可为空） */
    private String path;
    /** 权限标识符（如 sys:user:list，角色授权时使用） */
    private String perms;
    /** 显示排序 */
    private Integer sort;
    /** 是否可见：0-隐藏（不显示在侧边栏），1-显示 */
    private Integer visible;
    /** 状态：0-禁用，1-启用 */
    private Integer status;

}
