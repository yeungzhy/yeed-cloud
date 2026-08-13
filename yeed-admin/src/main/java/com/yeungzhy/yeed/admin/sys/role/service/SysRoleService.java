package com.yeungzhy.yeed.admin.sys.role.service;

import com.yeungzhy.yeed.admin.sys.role.dto.SysRoleDTO;
import com.yeungzhy.yeed.admin.sys.role.dto.SysRoleMenuGrantDTO;
import com.yeungzhy.yeed.admin.sys.role.dto.SysRolePageDTO;
import com.yeungzhy.yeed.admin.sys.role.vo.SysRolePageVO;
import com.yeungzhy.yeed.admin.sys.role.vo.SysRoleVO;
import com.yeungzhy.yeed.common.core.request.StatusRequest;
import com.yeungzhy.yeed.common.core.result.PageResult;

import java.util.Collection;
import java.util.List;

/**
 * 系统角色 服务类
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:14
 */
public interface SysRoleService {

    // ==================== 标准写入（CUD） ====================

    /**
     * 新增
     *
     * @param dto 入参
     * @return 新增记录的主键 ID
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    Long save(SysRoleDTO dto);

    /**
     * 更新
     *
     * @param dto 入参
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    void update(SysRoleDTO dto);

    /**
     * 更新状态
     *
     * @param dto 入参
     * @author yeungzhy
     * @since 2026-08-13 07:53
     */
    void updateStatus(StatusRequest dto);


    // ==================== 标准查询（R） ====================

    /**
     * 详情
     *
     * @param id 主键 ID
     * @return 详情数据
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    SysRoleVO detail(Long id);

    /**
     * 分页查询
     *
     * @param dto 分页查询入参
     * @return 分页结果（仅业务字段，不含审计字段）
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    PageResult<SysRolePageVO> page(SysRolePageDTO dto);


    // ==================== 角色菜单授权 ====================

    /**
     * 保存角色菜单授权（全量覆盖）
     * <p>menuIds 为该角色最终的完整权限集合（含按钮权限点）：先清空旧关联，再批量写入新关联。
     *
     * @param dto 授权入参
     * @author yeungzhy
     * @since 2026-08-13 07:53
     */
    void grantMenus(SysRoleMenuGrantDTO dto);

    /**
     * 查询角色已授权的菜单 ID 集合（授权页回显）
     *
     * @param roleId 角色 ID
     * @return 已授权菜单 ID 集合
     */
    List<Long> listMenuIdsByRole(Long roleId);


    // ==================== 标准删除（逻辑删除） ====================

    /**
     * 删除（逻辑删除）
     *
     * @param id 主键 ID
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    void delete(Long id);

    /**
     * 批量删除（逻辑删除）
     *
     * @param ids 主键 ID 集合
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    void delete(Collection<Long> ids);


}
