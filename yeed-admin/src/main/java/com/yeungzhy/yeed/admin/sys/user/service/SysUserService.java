package com.yeungzhy.yeed.admin.sys.user.service;

import com.yeungzhy.yeed.admin.sys.user.dto.SysUserAddDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserPageDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserRoleGrantDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserUpdateDTO;
import com.yeungzhy.yeed.admin.sys.user.vo.SysUserVO;
import com.yeungzhy.yeed.api.user.dto.UserVerifyDTO;
import com.yeungzhy.yeed.common.core.result.PageResult;
import com.yeungzhy.yeed.common.core.security.LoginUserInfo;

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
     * 凭据校验：校验账号密码，校验通过返回登录身份包
     *
     * @param dto 账号 + 密码
     * @return 登录身份包
     * @author yeungzhy
     * @since 2026-08-09
     */
    LoginUserInfo verify(UserVerifyDTO dto);


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
