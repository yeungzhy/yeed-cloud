package com.yeungzhy.yeed.admin.sys.menu.service;

import com.yeungzhy.yeed.admin.sys.menu.dto.SysMenuDTO;
import com.yeungzhy.yeed.admin.sys.menu.dto.SysMenuPageDTO;
import com.yeungzhy.yeed.admin.sys.menu.vo.SysMenuVO;
import com.yeungzhy.yeed.common.core.result.PageResult;

/**
 * 系统菜单权限表 服务类
 *
 * @author yeungzhy
 * @since 2026-08-09 10:26:00
 */
public interface SysMenuService {

    /**
     * 新增
     *
     * @param dto 入参
     * @return 新增记录的主键 ID
     * @author yeungzhy
     * @since 2026-08-09 16:28:56
     */
    Long save(SysMenuDTO dto);

    /**
     * 详情
     *
     * @param id 主键 ID
     * @return 详情数据
     * @author yeungzhy
     * @since 2026-08-09 16:28:56
     */
    SysMenuVO detail(Long id);

    /**
     * 更新
     *
     * @param dto 入参
     * @author yeungzhy
     * @since 2026-08-09 16:28:56
     */
    void update(SysMenuDTO dto);

    /**
     * 分页查询
     *
     * @param dto 分页查询入参
     * @return 分页结果
     * @author yeungzhy
     * @since 2026-08-09 16:28:56
     */
    PageResult<SysMenuVO> page(SysMenuPageDTO dto);

    /**
     * 删除（逻辑删除）
     *
     * @param id 主键 ID
     * @author yeungzhy
     * @since 2026-08-09 16:28:56
     */
    void delete(Long id);

}
