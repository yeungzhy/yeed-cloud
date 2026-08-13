package com.yeungzhy.yeed.admin.sys.role.service;

import com.yeungzhy.yeed.admin.sys.role.dto.SysRoleDTO;
import com.yeungzhy.yeed.admin.sys.role.dto.SysRolePageDTO;
import com.yeungzhy.yeed.admin.sys.role.vo.SysRoleVO;
import com.yeungzhy.yeed.common.core.request.StatusRequest;
import com.yeungzhy.yeed.common.core.result.PageResult;

import java.util.Collection;

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
     * @return 分页结果
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    PageResult<SysRoleVO> page(SysRolePageDTO dto);


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


    // ==================== 特殊操作（回收站 & 危险操作） ====================

    /**
     * 分页查询已逻辑删除的数据，如回收站列表
     *
     * @return 分页结果
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    PageResult<SysRoleVO> pageWithDeleted(SysRolePageDTO dto);

    /**
     * 恢复已逻辑删除的数据
     *
     * @param id 主键 ID
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    void restoreById(Long id);

    /**
     * 物理删除（真 DELETE，不可恢复，仅用于"回收站彻底删除"等场景）
     *
     * @param id 主键 ID
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    void physicalDeleteById(Long id);

}
