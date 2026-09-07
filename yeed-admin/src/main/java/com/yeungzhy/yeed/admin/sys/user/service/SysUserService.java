package com.yeungzhy.yeed.admin.sys.user.service;

import com.yeungzhy.yeed.admin.sys.user.dto.SysUserAddDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserPageDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserRoleGrantDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserUpdateDTO;
import com.yeungzhy.yeed.admin.sys.user.vo.SysUserVO;
import com.yeungzhy.yeed.api.user.dto.UserVerifyDTO;
import com.yeungzhy.yeed.common.core.result.PageResult;
import com.yeungzhy.yeed.common.core.security.LoginUserInfo;
import com.yeungzhy.yeed.common.core.security.MenuTreeInfo;

import java.util.Collection;
import java.util.List;

/**
 * 系统用户 服务类
 *
 * @author yeungzhy
 * @since 2026-08-13 06:48:01
 */
public interface SysUserService {

    // ==================== 标准写入（CUD） ====================

    /**
     * 新增
     *
     * @param dto 入参
     * @return 新增记录的主键 ID
     * @author yeungzhy
     * @since 2026-08-13 06:48:01
     */
    Long save(SysUserAddDTO dto);

    /**
     * 更新
     *
     * @param dto 入参
     * @author yeungzhy
     * @since 2026-08-13 06:48:01
     */
    void update(SysUserUpdateDTO dto);


    // ==================== 标准查询（R） ====================

    /**
     * 详情
     *
     * @param id 主键 ID
     * @return 详情数据
     * @author yeungzhy
     * @since 2026-08-13 06:48:01
     */
    SysUserVO detail(Long id);

    /**
     * 分页查询
     *
     * @param dto 分页查询入参
     * @return 分页结果
     * @author yeungzhy
     * @since 2026-08-13 06:48:01
     */
    PageResult<SysUserVO> page(SysUserPageDTO dto);

    /**
     * 按查询条件统计命中行数（不计分页字段）
     *
     * <p>与 {@link #page} 共用同一套查询条件，二者口径必然一致；不复用 page 的结果取 total，
     * 是因为那是「count + 取首页」两条 SQL、白查一遍首页数据。
     *
     * @param dto 查询条件
     * @return 命中行数
     * @since 2026-09-07
     */
    Long count(SysUserPageDTO dto);

    /**
     * 按页取数，不做 count
     *
     * <p>供「总数已在循环外取得、顺序翻页」的批量取数使用（异步导出）：省掉每页一次的
     * {@code SELECT COUNT(*)}。返回 List 而非 PageResult——没有 count 就没有可信的 total。
     *
     * @param dto 查询条件 + 分页参数（pageNum / pageSize）
     * @return 本页数据；无数据时为空列表（不为 null）
     * @since 2026-09-07
     */
    List<SysUserVO> slice(SysUserPageDTO dto);


    /**
     * 凭据校验：校验账号密码，校验通过返回登录身份包
     *
     * <p>身份包只含"服务端鉴权所需"数据（用户信息 + 角色编码 + 权限标识）；
     * 前端渲染用的菜单树由 {@link #listMenusByUserId} 单独装配，不进登录会话。
     *
     * @param dto 账号 + 密码
     * @return 登录身份包
     * @author yeungzhy
     * @since 2026-08-09
     */
    LoginUserInfo verify(UserVerifyDTO dto);

    /**
     * 查询用户可见菜单树（登录响应/用户菜单接口出参）
     *
     * <p>仅目录/菜单节点建树（按钮不承载路由），超管取全量；属于前端渲染数据，
     * 不写入登录会话（session 只存 {@link LoginUserInfo} 身份包）。
     *
     * @param userId 用户 ID
     * @return 已建树的菜单树节点列表（顶级节点）
     * @author yeungzhy
     * @since 2026-08-15
     */
    List<MenuTreeInfo> listMenusByUserId(Long userId);


    // ==================== 用户角色授权 ====================

    /**
     * 保存用户角色授权（全量覆盖）
     * <p>roleIds 为该用户最终的完整角色集合：先清空旧关联，再批量写入新关联。
     *
     * @param dto 授权入参（用户ID + 角色ID集合）
     * @author yeungzhy
     * @since 2026-08-13 06:48:01
     */
    void grantRoles(SysUserRoleGrantDTO dto);

    /**
     * 查询用户已分配的角色 ID 集合（授权页回显）
     *
     * @param userId 用户 ID
     * @return 已分配角色 ID 集合
     * @author yeungzhy
     * @since 2026-08-13 06:48:01
     */
    List<Long> listRoleIdsByUser(Long userId);


    // ==================== 标准删除（逻辑删除） ====================

    /**
     * 删除（逻辑删除）
     *
     * @param id 主键 ID
     * @author yeungzhy
     * @since 2026-08-13 06:48:01
     */
    void delete(Long id);

    /**
     * 批量删除（逻辑删除）
     *
     * @param ids 主键 ID 集合
     * @author yeungzhy
     * @since 2026-08-13 06:48:01
     */
    void delete(Collection<Long> ids);


}
