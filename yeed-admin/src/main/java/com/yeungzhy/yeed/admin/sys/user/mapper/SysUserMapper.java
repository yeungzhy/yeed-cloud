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
     * 查询用户启用的角色编码集合
     *
     * @param userId 用户ID
     * @return 角色编码列表（去重）
     */
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);


    /**
     * 查询用户启用的菜单行（装配 perms 串与菜单树共用，一次查询两处投影）
     *
     * @param userId 用户ID
     * @return 菜单行列表（按 sort 排序，已去重）
     */
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);



}
