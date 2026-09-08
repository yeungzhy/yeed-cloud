package com.yeungzhy.yeed.admin.sys.user.mapper;

import com.yeungzhy.yeed.admin.sys.menu.entity.SysMenu;
import com.yeungzhy.yeed.admin.sys.user.entity.SysUser;
import com.yeungzhy.yeed.common.data.mybatis.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统用户 Mapper 接口
 *
 * @author yeungzhy
 * @since 2026-08-13 06:48:01
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {


    /**
     * 查询用户已启用角色的编码集合
     *
     * <p>禁用角色不返回：它既是超管判定依据，也是授权装配的输入，禁用角色不该继续生效
     *
     * @param userId 用户 ID，不能为 null
     * @return 角色编码列表（已去重）；无角色时为空列表
     */
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);


    /**
     * 查询用户可见的菜单行（装配 perms 与菜单树共用，一次查询两处投影）
     *
     * @param userId 用户 ID，不能为 null；超管不查这里，走全量菜单
     * @return 菜单行列表（按 sort 升序，已去重）
     */
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);



}
