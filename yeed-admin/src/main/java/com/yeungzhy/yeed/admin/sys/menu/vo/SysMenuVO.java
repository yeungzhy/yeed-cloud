package com.yeungzhy.yeed.admin.sys.menu.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 系统菜单权限表 VO（返回出参）
 *
 * @author yeungzhy
 * @since 2026-08-09 10:26:00
 */
@Data
@Accessors(chain = true)
public class SysMenuVO {

    // ================== 主键 ==================
    /** 雪花ID主键 */
    private Long id;

    // ================== 业务字段 ==================
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

    // ================== 审计字段 ==================
    /** 创建人 */
    private Long createBy;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新人 */
    private Long updateBy;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 删除人 */
    private Long deleteBy;
    /** 逻辑删除,0-未删,时间戳-已删 */
    private Long deleteTime;
    /** 乐观锁 */
    private Integer version;
    /** 扩展信息 */
    private Map<String, Object> extra;

}
