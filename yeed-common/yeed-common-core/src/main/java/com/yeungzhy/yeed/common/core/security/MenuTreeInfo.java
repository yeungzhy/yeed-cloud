package com.yeungzhy.yeed.common.core.security;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 菜单树节点（前端渲染）
 *
 * <p> 菜单树是前端 UI 数据，不属于服务端鉴权依据（鉴权只看权限码串）
 * <p> 因此会话 session 只存 {@link LoginUserInfo} 身份包
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@Data
@Accessors(chain = true)
public class MenuTreeInfo {

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
