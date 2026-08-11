package com.yeungzhy.yeed.admin.sys.role.service;

import com.yeungzhy.yeed.admin.sys.role.dto.SysRoleDTO;
import com.yeungzhy.yeed.admin.sys.role.dto.SysRolePageDTO;
import com.yeungzhy.yeed.admin.sys.role.vo.SysRoleVO;
import com.yeungzhy.yeed.common.core.result.PageResult;

/**
 * 系统角色 服务类
 *
 * @author yeungzhy
 * @since 2026-08-09 10:25:37
 */
public interface SysRoleService {

    /**
     * 新增
     *
     * @param dto 入参
     * @return 新增记录的主键 ID
     * @author yeungzhy
     * @since 2026-08-09 16:28:41
     */
    Long save(SysRoleDTO dto);

    /**
     * 详情
     *
     * @param id 主键 ID
     * @return 详情数据
     * @author yeungzhy
     * @since 2026-08-09 16:28:41
     */
    SysRoleVO detail(Long id);

    /**
     * 更新
     *
     * @param dto 入参
     * @author yeungzhy
     * @since 2026-08-09 16:28:41
     */
    void update(SysRoleDTO dto);

    /**
     * 分页查询
     *
     * @param dto 分页查询入参
     * @return 分页结果
     * @author yeungzhy
     * @since 2026-08-09 16:28:41
     */
    PageResult<SysRoleVO> page(SysRolePageDTO dto);

    /**
     * 删除（逻辑删除）
     *
     * @param id 主键 ID
     * @author yeungzhy
     * @since 2026-08-09 16:28:41
     */
    void delete(Long id);

}
