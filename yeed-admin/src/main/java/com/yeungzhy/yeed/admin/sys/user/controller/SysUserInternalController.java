package com.yeungzhy.yeed.admin.sys.user.controller;

import com.yeungzhy.yeed.admin.sys.user.service.SysUserService;
import com.yeungzhy.yeed.api.user.dto.UserMenuDTO;
import com.yeungzhy.yeed.api.user.dto.UserVerifyDTO;
import com.yeungzhy.yeed.common.core.security.LoginUserInfo;
import com.yeungzhy.yeed.common.core.security.MenuTreeInfo;
import com.yeungzhy.yeed.common.web.annotation.InternalApi;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统用户 内部接口控制器（仅供 auth / job 经 Feign 调用）
 *
 * <p>挂载于 {@code /internal/**} 前缀：
 * <ul>
 *   <li>网关层应未定义该前缀的外部路由，避免凭据校验接口被外部直调；</li>
 * </ul>
 *
 * <p>RPC-Style：成功直接返回业务数据（裸返回）；失败抛异常，由
 * {@code InternalApiExceptionHandler} 统一映射为 HTTP 错误码 + ApiResult body，
 * 消费方经 {@code InternalErrorDecoder} 还原为 {@code BizException}。
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@Slf4j
@Validated
@InternalApi
@RestController
@RequestMapping("/internal/user")
public class SysUserInternalController {

    @Resource
    private SysUserService sysUserService;


    /**
     * 凭据校验：校验账号密码，校验通过返回登录身份包
     *
     * @param dto 账号 + 密码
     * @return 登录身份包（用户信息 + 角色编码 + 权限标识；菜单树走 user-menus 单独接口）
     */
    @PostMapping("/verify")
    public ApiResult<LoginUserInfo> verify(@Valid @RequestBody UserVerifyDTO dto) {
        return ApiResult.ok(sysUserService.verify(dto));
    public LoginUserInfo verify(@Valid @RequestBody UserVerifyDTO dto) {
        return sysUserService.verify(dto);
    }

    /**
     * 查询用户可见菜单树（前端侧边栏渲染；登录响应由 auth 组装）
     *
     * @param dto 用户 ID
     * @return 已建树的菜单树节点（仅目录/菜单，不含按钮）
     */
    @PostMapping("/user-menus")
    public List<MenuTreeInfo> userMenus(@Valid @RequestBody UserMenuDTO dto) {
        return sysUserService.listMenusByUserId(dto.getUserId());
    }

}
