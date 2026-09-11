package com.yeungzhy.yeed.common.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.yeungzhy.yeed.common.core.security.LoginSessionStore;
import com.yeungzhy.yeed.common.core.security.LoginUserInfo;
import com.yeungzhy.yeed.common.core.security.SessionKeys;

/**
 * 基于 Sa-Token 会话的 {@link LoginSessionStore} 实现
 *
 * <p> 登录态落在 Redis（JWT Simple 模式），token 校验与权限读取每次回落会话，
 * 因此覆盖会话或注销会话能立即作用于该账号的全部在线端
 *
 * @author yeungzhy
 * @since 2026-09-11
 * @see com.yeungzhy.yeed.common.security.config.SecurityAutoConfiguration
 */
public class SaTokenLoginSessionStore implements LoginSessionStore {

    /**
     * 覆盖会话中的登录身份包
     *
     * @param userId    用户主键
     * @param loginUser 最新身份包
     */
    @Override
    public void overwriteLoginUser(Long userId, LoginUserInfo loginUser) {
        // getSessionByLoginId(userId, false)：账号无会话时返回 null，不新建
        SaSession session = StpUtil.getSessionByLoginId(userId, false);
        if (session == null) {
            return;
        }
        session.set(SessionKeys.LOGIN_USER, loginUser);
    }

    /**
     * 注销指定账号的全部会话
     *
     * @param userId 用户主键
     */
    @Override
    public void invalidate(Long userId) {
        StpUtil.kickout(userId);
    }

}
