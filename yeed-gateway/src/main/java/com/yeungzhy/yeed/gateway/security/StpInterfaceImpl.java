package com.yeungzhy.yeed.gateway.security;

import cn.dev33.satoken.stp.StpInterface;
import com.yeungzhy.yeed.common.core.security.LoginUserHelper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 实现 Sa-Token 权限验证接口
 *
 * <p>权限/角色取自登录会话（LoginUserHelper → SaSession → Redis）：登录时由 auth 一次性
 * 查询并写入会话，网关鉴权链路只有 Redis 会话直读，零 DB / 零远程调用，符合 reactive 网关纯响应式要求
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // getPerms() 恒非 null（未登录/无权限时为空集合），无需判空
        return LoginUserHelper.getPerms();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return LoginUserHelper.getRoleCodes();
    }

}
