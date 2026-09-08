package com.yeungzhy.yeed.admin.sys.menu.dto;

import com.yeungzhy.yeed.admin.sys.menu.enums.MenuTypeEnum;
import com.yeungzhy.yeed.admin.sys.menu.enums.MenuVisibleEnum;
import com.yeungzhy.yeed.common.web.clean.CleanLevel;
import com.yeungzhy.yeed.common.web.clean.CleanString;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 系统菜单 新增入参
 *
 * <p>只承载业务字段：不含主键 id（雪花生成）与审计字段（框架自动填充），前端伪造不了，
 * 更新入参见 {@link SysMenuUpdateDTO}
 *
 * <p>按钮类型的 perms 由 path 派生，前端传了也会被覆盖
 *
 * @author yeungzhy
 * @since 2026-08-13
 */
@Data
@Accessors(chain = true)
public class SysMenuSaveDTO {

    /** 父菜单ID，0-顶级菜单（必填，不存在 null） */
    private Long parentId;
    /** 菜单名称（反序列化时去除首尾空白字符） */
    @CleanString(CleanLevel.TRIM)
    private String menuName;
    /** 菜单类型（目录/菜单/按钮） */
    private MenuTypeEnum menuType;
    /** 路由地址（目录/菜单页面对应前端路由，按钮可为空；反序列化时去除全部空白字符） */
    @CleanString(CleanLevel.ALL)
    private String path;
    /** 权限标识符（如 sys:user:list，角色授权时使用；反序列化时去除全部空白字符） */
    @CleanString(CleanLevel.ALL)
    private String perms;
    /** 显示排序 */
    private Integer sort;
    /** 是否可见（侧边栏显示/隐藏） */
    private MenuVisibleEnum visible;

}
