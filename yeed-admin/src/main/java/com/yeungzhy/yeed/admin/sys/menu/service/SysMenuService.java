package com.yeungzhy.yeed.admin.sys.menu.service;

import com.yeungzhy.yeed.admin.sys.menu.dto.SysMenuMoveDTO;
import com.yeungzhy.yeed.admin.sys.menu.dto.SysMenuPageDTO;
import com.yeungzhy.yeed.admin.sys.menu.dto.SysMenuSaveDTO;
import com.yeungzhy.yeed.admin.sys.menu.dto.SysMenuUpdateDTO;
import com.yeungzhy.yeed.admin.sys.menu.vo.SysMenuPageVO;
import com.yeungzhy.yeed.admin.sys.menu.vo.SysMenuTreeVO;
import com.yeungzhy.yeed.admin.sys.menu.vo.SysMenuVO;
import com.yeungzhy.yeed.common.core.result.PageResult;

import java.util.Collection;
import java.util.List;

/**
 * 系统菜单权限表 服务类
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:30
 */
public interface SysMenuService {

    // ==================== 标准写入（CUD） ====================

    /**
     * 新增
     *
     * @param dto 新增入参（仅业务字段，不含 id/审计字段）
     * @return 新增记录的主键 ID
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    Long save(SysMenuSaveDTO dto);

    /**
     * 更新
     *
     * @param dto 更新入参（含 id 与 version 乐观锁）
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    void update(SysMenuUpdateDTO dto);

    /**
     * 拖拽调整层级（移动菜单到新的父级下，防循环依赖）
     *
     * @param dto 移动入参（id + 目标 parentId）
     */
    void move(SysMenuMoveDTO dto);


     // ==================== 接口权限缓存维护 ====================

    /**
     * 全量重建菜单接口权限缓存（Map＜接口路径, 权限码＞）
     * <p>启动预热与菜单写操作自愈共用的唯一重建入口：查全量按钮菜单写入
     */
    void reloadPermsCache();

    /**
     * 懒加载重建：缓存整体缺失时重建，存在则跳过
     * <p>挂在菜单树查询侧做外部清库后的自愈（如超管进菜单管理页即恢复），避免依赖菜单写操作
     */
    void reloadPermsCacheIfAbsent();


    // ==================== 标准查询（R） ====================

    /**
     * 详情
     *
     * @param id 主键 ID
     * @return 详情数据
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    SysMenuVO detail(Long id);

    /**
     * 分页查询
     *
     * @param dto 分页查询入参
     * @return 分页结果（仅业务字段，不含审计字段）
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    PageResult<SysMenuPageVO> page(SysMenuPageDTO dto);

    /**
     * 菜单树（菜单管理页 / 角色授权树形选择器共用）
     * <p>返回含按钮权限点的完整树，按 sort 升序；树节点仅承载业务字段。
     * 侧边栏渲染不依赖本接口——侧边栏菜单由用户拥有的角色权限动态组装。
     *
     * @return 完整菜单树
     */
    List<SysMenuTreeVO> tree();


    // ==================== 标准删除（逻辑删除） ====================

    /**
     * 删除（逻辑删除）
     *
     * @param id 主键 ID
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    void delete(Long id);

    /**
     * 批量删除（逻辑删除）
     *
     * @param ids 主键 ID 集合
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    void delete(Collection<Long> ids);


}
