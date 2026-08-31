package com.yeungzhy.yeed.gateway.filter;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import com.yeungzhy.yeed.common.core.security.LoginUserHelper;
import com.yeungzhy.yeed.common.core.security.LoginUserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 登录用户快照过滤器
 *
 * <p>在请求入口读取当前登录身份，存入 exchange 属性供下游复用；取不到（匿名请求）时不写。
 * 唯一职责是身份捕获，不做鉴权、不做记录。
 *
 * <p>前置于 SaReactorFilter，鉴权失败（401/403）的请求才能记录"谁尝试了越权访问"。
 *
 * <p>为什么用 exchange 属性传递而非各消费者自己读：
 * <ul>
 *   <li>上下文是 ThreadLocal，跨线程即失效；提取成普通对象后可安全异步消费；</li>
 *   <li>身份在入口一次性确定，语义是"谁发起的请求"，而非"响应写完时会话里剩谁"；</li>
 *   <li>后续 filter / handler 可零成本复用。</li>
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-08-30
 */
@Slf4j
public class LoginUserSnapshotFilter implements WebFilter {

    /** 登录用户快照在 exchange 属性中的键；匿名请求不写入该键 */
    public static final String LOGIN_USER_SNAPSHOT_KEY = "loginUserSnapshotKey";

    @Override
    @SuppressWarnings("NullableProblems")
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        LoginUserInfo loginUser = captureLoginUser(exchange);
        if (loginUser != null) {
            // attributes 是 ConcurrentHashMap, put(key, null) 抛 NPE
            exchange.getAttributes().put(LOGIN_USER_SNAPSHOT_KEY, loginUser);
        }
        return chain.filter(exchange);
    }


    /**
     * 绑定上下文后读取登录身份
     *
     * <p>取不到属正常态（放行名单的匿名请求、尚未登录的登录接口），不打日志；
     * 只有真正异常才 warn 并降级为匿名——记录日志的失败不能影响请求本身
     */
    private static LoginUserInfo captureLoginUser(ServerWebExchange exchange) {
        try {
            SaReactorSyncHolder.setContext(exchange);
            return LoginUserHelper.getLoginUser();
        } catch (Exception e) {
            log.warn("读取登录用户失败，按匿名处理：path={}", exchange.getRequest().getPath(), e);
            return null;
        } finally {
            // Netty 复用 event loop 线程，漏清理会让下一个请求读到上一个用户的身份
            SaReactorSyncHolder.clearContext();
        }
    }

}
