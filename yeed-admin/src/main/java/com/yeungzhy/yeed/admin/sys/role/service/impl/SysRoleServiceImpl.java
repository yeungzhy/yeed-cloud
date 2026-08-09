package com.yeungzhy.yeed.admin.sys.role.service.impl;

import com.yeungzhy.yeed.admin.sys.role.mapper.SysRoleMapper;
import com.yeungzhy.yeed.admin.sys.role.service.SysRoleService;
import com.yeungzhy.yeed.admin.sys.role.service.SysRoleSorts;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 系统角色 服务实现类
 *
 * @author yeungzhy
 * @since 2026-08-09 10:25:37
 */
@Slf4j
@Service
public class SysRoleServiceImpl implements SysRoleService {

    @Resource
    private SysRoleSorts sysRoleSorts;
    @Resource
    private SysRoleMapper sysRoleMapper;



}
