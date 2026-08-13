package com.yeungzhy.yeed.admin.sys.menu.dto;

import com.yeungzhy.yeed.admin.sys.menu.enums.MenuTypeEnum;
import com.yeungzhy.yeed.admin.sys.menu.enums.MenuVisibleEnum;
import com.yeungzhy.yeed.common.core.request.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 系统菜单权限表 分页查询 DTO（前端入参）
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:30
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class SysMenuPageDTO extends PageRequest {

    /** 菜单名称 */
    private String menuName;
    /** 菜单类型（目录/菜单/按钮） */
    private MenuTypeEnum menuType;
    /** 路由地址（目录/菜单页面对应前端路由，按钮可为空） */
    private String path;
    /** 权限标识符（如 sys:user:list，角色授权时使用） */
    private String perms;
    /** 显示排序 */
    private Integer sort;
    /** 是否可见（侧边栏显示/隐藏） */
    private MenuVisibleEnum visible;

}
