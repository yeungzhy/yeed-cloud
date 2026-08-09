package com.yeungzhy.yeed.admin.sys.menu.controller;

import com.yeungzhy.yeed.admin.sys.menu.service.SysMenuService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统菜单权限表 前端控制器
 *
 * @author yeungzhy
 * @since 2026-08-09 10:26:00
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/menu/sys-menu")
public class SysMenuController {

    @Resource
    private SysMenuService sysMenuService;



}
