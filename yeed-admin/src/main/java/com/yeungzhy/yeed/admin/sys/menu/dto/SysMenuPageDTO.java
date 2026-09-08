package com.yeungzhy.yeed.admin.sys.menu.dto;

import com.yeungzhy.yeed.admin.sys.menu.enums.MenuTypeEnum;
import com.yeungzhy.yeed.admin.sys.menu.enums.MenuVisibleEnum;
import com.yeungzhy.yeed.common.core.request.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 系统菜单 分页查询 DTO（前端入参）
 *
 * <p>path / perms / sort 三个字段当前不参与查询条件，保留是为了与实体字段对齐；
 * 真正生效的筛选是 menuName（模糊）、menuType 与 visible（精确）
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
    /** 路由地址（不参与过滤） */
    private String path;
    /** 权限标识符（不参与过滤） */
    private String perms;
    /** 显示排序（不参与过滤） */
    private Integer sort;
    /** 是否可见（侧边栏显示/隐藏） */
    private MenuVisibleEnum visible;

}
