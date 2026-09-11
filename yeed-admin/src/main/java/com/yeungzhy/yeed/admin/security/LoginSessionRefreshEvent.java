package com.yeungzhy.yeed.admin.security;

import java.util.Collection;
import java.util.List;

/**
 * 登录会话刷新事件
 *
 * <p> 授权或用户状态变更后发布，事务提交后由 {@link LoginSessionRefreshListener} 用数据库最新数据重算会话，
 * 使权限收窄对所有在线端立即生效
 *
 * @param userIds 受影响的用户主键
 * @author yeungzhy
 * @since 2026-09-11
 */
public record LoginSessionRefreshEvent(Collection<Long> userIds) {

    /**
     * 单用户场景的便捷构造
     *
     * @param userId 用户主键
     * @return 刷新事件
     */
    public static LoginSessionRefreshEvent of(Long userId) {
        return new LoginSessionRefreshEvent(List.of(userId));
    }

}
