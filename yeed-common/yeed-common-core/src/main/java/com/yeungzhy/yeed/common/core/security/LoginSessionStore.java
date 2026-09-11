package com.yeungzhy.yeed.common.core.security;

/**
 * 登录会话维护抽象接口（管理端在授权或状态变更后调用）
 *
 * <p> 权限收窄必须与数据库同步落定，否则旧会话在有效期内继续持有已收回的权限
 * <p> 本项目的登录身份快照存于账号级会话（同账号全部 token 共享），覆盖快照即覆盖所有在线端
 *
 * @author yeungzhy
 * @since 2026-09-11
 */
public interface LoginSessionStore {

    /**
     * 用最新身份快照覆盖会话中的登录身份包
     *
     * <p> 账号当前无会话时静默跳过，不新建会话
     *
     * @param userId    用户主键
     * @param loginUser 最新身份包
     */
    void overwriteLoginUser(Long userId, LoginUserInfo loginUser);

    /**
     * 注销指定账号的全部会话
     *
     * <p> 账号被禁用或删除后调用：此时仅清空权限不够，无需权限码的接口仍可访问
     *
     * @param userId 用户主键
     */
    void invalidate(Long userId);

}
