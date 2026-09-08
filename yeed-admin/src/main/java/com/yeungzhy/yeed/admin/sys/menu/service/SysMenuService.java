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
 * 系统菜单 服务门面
 *
 * <p>parentId 一律非 null，0 表示顶级；写入侧拦父级不存在与循环依赖，
 * 查询侧对存量脏数据只告警不阻断，避免一处脏数据让菜单管理页整体不可用
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:30
 */
public interface SysMenuService {

    // ==================== 标准写入（CUD） ====================

    /**
     * 新增菜单
     *
     * @param dto 新增入参，menuName / menuType / parentId 必填；按钮类型还须带 path
     * @return 新增记录的主键 ID
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    Long save(SysMenuSaveDTO dto);

    /**
     * 更新菜单
     *
     * @param dto 更新入参，id 不能为空；目标父级不能是自身或自身后代
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    void update(SysMenuUpdateDTO dto);

    /**
     * 拖拽调整层级（只改 parentId，防循环依赖）
     *
     * @param dto 移动入参，id 与 parentId 均不能为空，parentId 传 0 表示移到顶级
     * @author yeungzhy
     * @since 2026-08-13
     */
    void move(SysMenuMoveDTO dto);


    // ==================== 标准查询（R） ====================

    /**
     * 菜单详情
     *
     * @param id 主键，不能为 null；不存在时抛业务异常
     * @return 详情，含审计字段与 version，供编辑回显
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    SysMenuVO detail(Long id);

    /**
     * 分页查询菜单
     *
     * @param dto 分页与筛选条件，筛选字段为 null 即不参与过滤
     * @return 分页结果，出参不含审计字段；无命中返回空页而非 null
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    PageResult<SysMenuPageVO> page(SysMenuPageDTO dto);

    /**
     * 菜单树（菜单管理页 / 角色授权树形选择器共用）
     *
     * <p>返回含按钮权限点的完整树，按 sort 升序，节点仅承载业务字段；侧边栏渲染不走这里，
     * 侧边栏菜单由当前用户拥有的角色权限动态组装
     *
     * <p>缓存整体缺失时顺带懒加载重建，外部清库后可自愈
     *
     * @return 完整菜单树；无菜单时为空列表，不为 null
     * @author yeungzhy
     * @since 2026-08-13
     */
    List<SysMenuTreeVO> tree();


    // ==================== 标准删除（逻辑删除） ====================

    /**
     * 删除菜单（逻辑删除）
     *
     * @param id 主键，不能为 null；存在子菜单时拒绝删除
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    void delete(Long id);

    /**
     * 批量删除菜单（逻辑删除）
     *
     * @param ids 主键集合，不能为空；任意一个有子菜单即整体拒绝，不做部分删除
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    void delete(Collection<Long> ids);


}
