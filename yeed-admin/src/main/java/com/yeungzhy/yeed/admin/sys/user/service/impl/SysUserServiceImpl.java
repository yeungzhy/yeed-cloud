package com.yeungzhy.yeed.admin.sys.user.service.impl;

import com.yeungzhy.yeed.admin.sys.user.mapper.SysUserMapper;
import com.yeungzhy.yeed.admin.sys.user.service.SysUserService;
import com.yeungzhy.yeed.admin.sys.user.service.SysUserSorts;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 系统用户 服务实现类
 *
 * @author yeungzhy
 * @since 2026-08-09 10:22:12
 */
@Slf4j
@Service
public class SysUserServiceImpl implements SysUserService {

    @Resource
    private SysUserSorts sysUserSorts;
    @Resource
    private SysUserMapper sysUserMapper;



}
