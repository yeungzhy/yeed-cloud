package com.yeungzhy.yeed.common.core.security;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 登录身份包
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@Data
@Accessors(chain = true)
public class LoginUserInfo {

    /** 用户主键 */
    private Long userId;
    /** 真实姓名（前台展示） */
    private String realName;
    /** 系统登录名 */
    private String username;
    /** 工号 */
    private String employeeNo;
    /** 角色编码集合 */
    private List<String> roleCodes;
    /** 有权限的菜单权限码集合 */
    private List<String> perms;
    /** 有权限的菜单树（仅目录 type=1/菜单 type=2，已建树），供前端渲染 */
    private List<MenuTreeInfo> menus;



    /**
     * 菜单树节点（前端渲染）
     *
     * @author yeungzhy
     * @since 2026-08-09
     */
    @Data
    @Accessors(chain = true)
    public static class MenuTreeInfo {

        /** 菜单ID */
        private Long id;
        /** 父菜单ID，0-顶级（不存在 null） */
        private Long parentId;
        /** 菜单名称 */
        private String menuName;
        /** 菜单类型：1-目录，2-菜单 */
        private Integer menuType;
        /** 路由地址（前端路由） */
        private String path;
        /** 权限标识（如 sys:user:list，按钮节点才有，目录/菜单通常为空） */
        private String perms;
        /** 是否可见：0-隐藏，1-显示 */
        private Integer visible;
        /** 显示排序 */
        private Integer sort;
        /** 子节点 */
        private List<MenuTreeInfo> children;

    }

}
