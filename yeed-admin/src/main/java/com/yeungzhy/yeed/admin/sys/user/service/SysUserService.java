package com.yeungzhy.yeed.admin.sys.user.service;

import com.yeungzhy.yeed.admin.sys.user.dto.SysUserAddDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserPageDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserPasswordDTO;
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
 * 系统用户 服务门面
 *
 * <p>登录入口是 username 与 employee_no 两个字段、共用同一命名空间，因此唯一性判定必须跨两列；
 * phone / email 是加密列，等值检索只能靠盲索引
 *
 * @author yeungzhy
 * @since 2026-08-13 06:48:01
 */
public interface SysUserService {

    // ==================== 标准写入（CUD） ====================

    /**
     * 新增用户
     *
     * @param dto 新增入参，username / password / employeeNo 必填；phone / email 可为空
     * @return 新增记录的主键 ID；账号、手机或邮箱撞号抛业务异常
     * @author yeungzhy
     * @since 2026-08-13 06:48:01
     */
    Long save(SysUserAddDTO dto);

    /**
     * 更新用户资料
     *
     * @param dto 更新入参，id 与 password 必填，password 用于校验操作人身份；
     *            各资料段为 null 或空白表示不修改
     * @author yeungzhy
     * @since 2026-08-13 06:48:01
     */
    void update(SysUserUpdateDTO dto);

    /**
     * 修改密码
     *
     * <p>{@code id} 为空时改当前登录用户自己的密码（校验原密码），否则由管理员重置指定用户密码（不校验原密码）；
     * 判定依据是当前登录态，不是"前端有没有传原密码"
     *
     * @param dto 入参（目标用户 ID + 原密码 + 新密码），newPassword 必填
     * @author yeungzhy
     * @since 2026-09-08
     */
    void changePassword(SysUserPasswordDTO dto);


    // ==================== 标准查询（R） ====================

    /**
     * 用户详情
     *
     * @param id 主键，不能为 null；不存在时抛业务异常
     * @return 详情，phone / email 经脱敏后再出参
     * @author yeungzhy
     * @since 2026-08-13 06:48:01
     */
    SysUserVO detail(Long id);

    /**
     * 分页查询用户
     *
     * @param dto 分页与筛选条件，筛选字段为 null 即不参与过滤
     * @return 分页结果；无命中返回空页而非 null
     * @author yeungzhy
     * @since 2026-08-13 06:48:01
     */
    PageResult<SysUserVO> page(SysUserPageDTO dto);

    /**
     * 按查询条件统计命中行数（不计分页字段）
     *
     * <p>与 {@link #page} 共用同一套查询条件，二者口径必然一致；不复用 page 的结果取 total，
     * 是因为那是「count + 取首页」两条 SQL、白查一遍首页数据
     *
     * @param dto 查询条件
     * @return 命中行数
     * @since 2026-09-07
     */
    Long count(SysUserPageDTO dto);

    /**
     * 按页取数，不做 count
     *
     * <p>供「总数已在循环外取得、顺序翻页」的批量取数使用（异步导出），省掉每页一次的
     * {@code SELECT COUNT(*)}；返回 List 而非 PageResult，没有 count 就没有可信的 total
     *
     * @param dto 查询条件 + 分页参数（pageNum / pageSize）
     * @return 本页数据；无数据时为空列表（不为 null）
     * @since 2026-09-07
     */
    List<SysUserVO> slice(SysUserPageDTO dto);


    /**
     * 凭据校验：校验账号密码，校验通过返回登录身份包
     *
     * <p>身份包只含服务端鉴权所需数据（用户信息 + 角色编码 + 权限标识）；
     * 前端渲染用的菜单树由 {@link #listMenusByUserId} 单独装配，不进登录会话
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
     *
     * <p>roleIds 是用户最终的完整角色集合，先清空旧关联再批量写入
     *
     * @param dto 授权入参，userId 与 roleIds 均不能为空
     * @author yeungzhy
     * @since 2026-08-13 06:48:01
     */
    void grantRoles(SysUserRoleGrantDTO dto);

    /**
     * 查询用户已分配的角色 ID 集合（授权页回显）
     *
     * @param userId 用户主键，不能为 null
     * @return 已分配角色 ID；未分配时为空列表，不为 null
     * @author yeungzhy
     * @since 2026-08-13 06:48:01
     */
    List<Long> listRoleIdsByUser(Long userId);


    // ==================== 标准删除（逻辑删除） ====================

    /**
     * 删除用户（逻辑删除）
     *
     * @param id 主键，不能为 null；用户角色关联不在此清理，逻辑删除行查不出来、不会造成越权
     * @author yeungzhy
     * @since 2026-08-13 06:48:01
     */
    void delete(Long id);

    /**
     * 批量删除用户（逻辑删除）
     *
     * @param ids 主键集合，不能为空；超量自动分片，删除人自动填充
     * @author yeungzhy
     * @since 2026-08-13 06:48:01
     */
    void delete(Collection<Long> ids);


}
