package com.yeungzhy.yeed.gateway.security;

import cn.dev33.satoken.stp.StpInterface;
import com.yeungzhy.yeed.common.core.security.LoginUserHelper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 实现 Sa-Token 权限验证接口
 *
 * <p>权限 / 角色取自登录会话（LoginUserHelper → SaSession → Redis）：登录时由 auth 一次性查好写进会话，
 * 网关鉴权只有 Redis 会话直读，零 DB、零远程调用，符合 reactive 网关的纯响应式要求
 *
 * @author yeungzhy
 * @since 2026-08-15
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    /**
     * 当前账号的权限码集合
     *
     * @param loginId   账号 ID，本实现不读取（身份取自请求上下文）
     * @param loginType 账号类型，本实现不读取
     * @return 权限码；未登录或无权限时为空列表，不为 null
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // getPerms() 恒非 null（未登录 / 无权限时为空集合），无需判空
        return LoginUserHelper.getPerms();
    }

    /**
     * 当前账号的角色编码集合
     *
     * @param loginId   账号 ID，本实现不读取
     * @param loginType 账号类型，本实现不读取
     * @return 角色编码；无角色时为空列表，不为 null
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return LoginUserHelper.getRoleCodes();
    }

}
