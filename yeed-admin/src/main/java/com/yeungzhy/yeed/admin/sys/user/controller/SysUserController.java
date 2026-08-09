package com.yeungzhy.yeed.admin.sys.user.controller;

import com.yeungzhy.yeed.admin.sys.user.service.SysUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统用户 前端控制器
 *
 * @author yeungzhy
 * @since 2026-08-09 10:22:12
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/user/sys-user")
public class SysUserController {

    @Resource
    private SysUserService sysUserService;



}
