package com.yeungzhy.yeed.admin.sys.menu.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yeungzhy.yeed.admin.sys.menu.enums.MenuTypeEnum;
import com.yeungzhy.yeed.admin.sys.menu.enums.MenuVisibleEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 系统菜单 树节点出参 VO（菜单管理页 / 角色授权树形选择器）
 *
 * <p>仅承载树节点展示所需业务字段；children 由 Service 调 {@link com.yeungzhy.yeed.common.core.support.TreeUtil}
 * 组装，不属 {@link com.yeungzhy.yeed.admin.sys.menu.service.SysMenuConvert} 的转换职责，
 * 审计字段同样不在树接口暴露（见 {@link SysMenuVO}）
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:30
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SysMenuTreeVO {

    // ================== 主键 ==================
    /** 雪花ID主键 */
    private Long id;

    // ================== 业务字段 ==================
    /** 父菜单ID，0-顶级菜单（不存在 null） */
    private Long parentId;
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
    /** 子菜单（由 Service 组装树时填充） */
    private List<SysMenuTreeVO> children;

}
