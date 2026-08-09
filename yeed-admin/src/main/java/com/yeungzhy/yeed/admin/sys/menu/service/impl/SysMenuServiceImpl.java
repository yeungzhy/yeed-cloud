package com.yeungzhy.yeed.admin.sys.menu.service.impl;

import com.yeungzhy.yeed.admin.sys.menu.mapper.SysMenuMapper;
import com.yeungzhy.yeed.admin.sys.menu.service.SysMenuService;
import com.yeungzhy.yeed.admin.sys.menu.service.SysMenuSorts;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 系统菜单权限表 服务实现类
 *
 * @author yeungzhy
 * @since 2026-08-09 10:26:00
 */
@Slf4j
@Service
public class SysMenuServiceImpl implements SysMenuService {

    @Resource
    private SysMenuSorts sysMenuSorts;
    @Resource
    private SysMenuMapper sysMenuMapper;



}
