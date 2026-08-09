package com.yeungzhy.yeed.admin.sys.role.controller;

import com.yeungzhy.yeed.admin.sys.role.service.SysRoleService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统角色 前端控制器
 *
 * @author yeungzhy
 * @since 2026-08-09 10:25:37
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/sys/role")
public class SysRoleController {

    @Resource
    private SysRoleService sysRoleService;



}
