package com.yeungzhy.yeed.api.user;

import com.yeungzhy.yeed.api.feign.config.InternalFeignConfig;
import com.yeungzhy.yeed.api.user.dto.UserMenuDTO;
import com.yeungzhy.yeed.api.user.dto.UserVerifyDTO;
import com.yeungzhy.yeed.common.core.security.LoginUserInfo;
import com.yeungzhy.yeed.common.core.security.MenuTreeInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 系统用户内部 Feign 契约（消费方：yeed-auth / yeed-job；提供方：yeed-admin）
 *
 * <p>RPC-Style：成功返回裸数据，业务失败（账号不存在 / 密码错误）由 admin 抛异常、经
 * {@code InternalErrorDecoder} 解码为 {@link com.yeungzhy.yeed.common.core.exception.BizException BizException}
 * 中断调用，调用方不必判 ApiResult 码
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@FeignClient(
        name = "yeed-admin",
        contextId = "sysUserFeignClient",
        path = "/internal/user",
        configuration = InternalFeignConfig.class
)
public interface SysUserFeignClient {

    /**
     * 校验账号密码，通过则返回登录身份包（用户信息 + 角色编码 + 权限标识）
     *
     * @param dto 账号与密码，均不能为空白；账号可以是登录名或工号，两者共用同一登录入口
     * @return 登录身份包；账号不存在、歧义或密码错误属业务失败，抛异常不返回
     */
    @PostMapping("/verify")
    LoginUserInfo verify(@RequestBody UserVerifyDTO dto);

    /**
     * 查询用户可见菜单树，用于登录响应与前端侧边栏渲染
     *
     * @param dto 用户主键，不能为 null；超管返回全量菜单
     * @return 已建树的菜单树节点（仅目录 / 菜单，不含按钮）；远程失败抛异常，由调用方决定是否降级
     */
    @PostMapping("/user-menus")
    List<MenuTreeInfo> userMenus(@RequestBody UserMenuDTO dto);

}
