package com.yeungzhy.yeed.admin.security;

import com.yeungzhy.yeed.admin.sys.user.service.SysUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 登录会话刷新监听器
 *
 * <p> 挂在事务提交后执行：同事务内重算权限是白算，事务一旦回滚，会话已被新的授权改写
 * <p> 逻辑删除等无事务的调用点同样需要刷新，故开启 fallbackExecution，无事务时立即执行
 * <p> 单个用户刷新失败只记日志：业务数据已提交，向上抛异常会让调用方拿到 500 却无从回滚
 *
 * @author yeungzhy
 * @since 2026-09-11
 */
@Slf4j
@Component
public class LoginSessionRefreshListener {

    @Resource
    private SysUserService sysUserService;

    /**
     * 逐个刷新受影响用户的登录会话
     *
     * <p> 选事务版而非 {@link EventListener}：后者在发布线程内立即执行，此时发布方事务尚未提交，
     * 一旦回滚，会话已被新授权改写且无法回滚
     *
     * @param event 刷新事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRefresh(LoginSessionRefreshEvent event) {
        for (Long userId : event.userIds()) {
            try {
                sysUserService.refreshLoginSession(userId);
            } catch (Exception e) {
                log.error("刷新登录会话失败, userId={}", userId, e);
            }
        }
    }

}
