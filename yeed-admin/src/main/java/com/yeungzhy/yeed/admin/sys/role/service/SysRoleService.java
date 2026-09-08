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
 * 系统角色 服务门面
 *
 * <p>内置角色受保护：不可改编码、改状态、删除。判定依据是 roleCode 命中内置白名单，
 * 由各写方法入口分别拦截，不依赖调用方自觉
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:14
 */
public interface SysRoleService {

    // ==================== 标准写入（CUD） ====================

    /**
     * 新增角色
     *
     * @param dto 新增入参，roleName / roleCode 均不能为空
     * @return 新增记录的主键 ID
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    Long save(SysRoleDTO dto);

    /**
     * 更新角色
     *
     * @param dto 更新入参，id 与 roleCode 不能为空；内置角色的 roleCode 不可变更
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    void update(SysRoleDTO dto);

    /**
     * 启停角色
     *
     * @param dto 主键与目标状态，均不能为空；内置角色一律拒绝
     * @author yeungzhy
     * @since 2026-08-13 07:53
     */
    void updateStatus(StatusRequest dto);


    // ==================== 标准查询（R） ====================

    /**
     * 角色详情
     *
     * @param id 主键，不能为 null；不存在时抛业务异常
     * @return 详情，含审计字段与 version，供编辑回显
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    SysRoleVO detail(Long id);

    /**
     * 分页查询角色
     *
     * @param dto 分页与筛选条件，筛选字段为 null 即不参与过滤
     * @return 分页结果，出参不含审计字段；无命中返回空页而非 null
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    PageResult<SysRolePageVO> page(SysRolePageDTO dto);


    // ==================== 角色菜单授权 ====================

    /**
     * 保存角色菜单授权（全量覆盖）
     *
     * <p>menuIds 是角色最终的完整权限集合（含按钮权限点），先清空旧关联再批量写入；
     * 祖先节点由服务端沿 parentId 补全，前端漏传也不会断链
     *
     * @param dto 授权入参，roleId 与 menuIds 均不能为空
     * @author yeungzhy
     * @since 2026-08-13 07:53
     */
    void grantMenus(SysRoleMenuGrantDTO dto);

    /**
     * 查询角色已授权的菜单 ID 集合（授权页回显）
     *
     * @param roleId 角色主键，不能为 null
     * @return 已授权菜单 ID；无授权时为空列表，不为 null
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    List<Long> listMenuIdsByRole(Long roleId);


    // ==================== 标准删除（逻辑删除） ====================

    /**
     * 删除角色（逻辑删除）
     *
     * @param id 主键，不能为 null；内置角色与已绑定用户的角色拒绝删除
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    void delete(Long id);

    /**
     * 批量删除角色（逻辑删除）
     *
     * @param ids 主键集合，不能为空；整批校验，任一不满足即整体拒绝
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    void delete(Collection<Long> ids);


}
