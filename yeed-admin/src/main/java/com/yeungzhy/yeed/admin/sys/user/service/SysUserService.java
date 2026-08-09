package com.yeungzhy.yeed.admin.sys.user.service;

import com.yeungzhy.yeed.admin.sys.user.dto.SysUserDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserPageDTO;
import com.yeungzhy.yeed.admin.sys.user.vo.SysUserVO;
import com.yeungzhy.yeed.common.result.PageResult;

/**
 * 系统用户 服务类
 *
 * @author yeungzhy
 * @since 2026-08-09 10:22:12
 */
public interface SysUserService {

    /**
     * 新增
     *
     * @param dto 入参
     * @return 新增记录的主键 ID
     * @author yeungzhy
     * @since 2026-08-09 16:28:25
     */
    Long save(SysUserDTO dto);

    /**
     * 详情
     *
     * @param id 主键 ID
     * @return 详情数据
     * @author yeungzhy
     * @since 2026-08-09 16:28:25
     */
    SysUserVO detail(Long id);

    /**
     * 更新
     *
     * @param dto 入参
     * @author yeungzhy
     * @since 2026-08-09 16:28:25
     */
    void update(SysUserDTO dto);

    /**
     * 分页查询
     *
     * @param dto 分页查询入参
     * @return 分页结果
     * @author yeungzhy
     * @since 2026-08-09 16:28:25
     */
    PageResult<SysUserVO> page(SysUserPageDTO dto);

    /**
     * 删除（逻辑删除）
     *
     * @param id 主键 ID
     * @author yeungzhy
     * @since 2026-08-09 16:28:25
     */
    void delete(Long id);

}
