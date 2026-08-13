package com.yeungzhy.yeed.admin.sys.menu.dto;

import com.yeungzhy.yeed.admin.sys.menu.enums.MenuTypeEnum;
import com.yeungzhy.yeed.admin.sys.menu.enums.MenuVisibleEnum;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 系统菜单权限表 新增入参
 * <p>仅承载业务字段：不含主键 id（雪花生成）与审计字段（框架自动填充），
 * 前端无法伪造；更新入参 {@link SysMenuUpdateDTO} 在此之上增加 id 与 version。
 *
 * @author yeungzhy
 * @since 2026-08-13
 */
@Data
@Accessors(chain = true)
public class SysMenuSaveDTO {

    /** 父菜单ID，0-顶级菜单（必填，不存在 null） */
    private Long parentId;
    /** 菜单名称（入库前去除全部空白字符） */
    private String menuName;
    /** 菜单类型（目录/菜单/按钮） */
    private MenuTypeEnum menuType;
    /** 路由地址（目录/菜单页面对应前端路由，按钮可为空；入库前去除全部空白字符） */
    private String path;
    /** 权限标识符（如 sys:user:list，角色授权时使用；入库前去除全部空白字符） */
    private String perms;
    /** 显示排序 */
    private Integer sort;
    /** 是否可见（侧边栏显示/隐藏） */
    private MenuVisibleEnum visible;

}
