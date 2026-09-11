package com.yeungzhy.yeed.admin.sys.user.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yeungzhy.yeed.admin.sys.user.entity.SysUserRole;
import com.yeungzhy.yeed.common.data.mybatis.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 用户角色关联表 Mapper 接口
 *
 * @author yeungzhy
 * @since 2026-08-13 06:56:05
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {


    /**
     * 查询角色下已授权的用户主键
     *
     * @param roleId 角色主键
     * @return 用户主键列表；无授权时为空列表
     */
    default List<Long> listUserIdsByRole(Long roleId) {
        List<SysUserRole> sysUserRoles = selectList(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getRoleId, roleId));
        return sysUserRoles.stream().map(SysUserRole::getUserId).toList();
    }


}
