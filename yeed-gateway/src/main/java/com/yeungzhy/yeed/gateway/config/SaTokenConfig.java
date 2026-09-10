package com.yeungzhy.yeed.gateway.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.yeungzhy.yeed.common.core.result.ApiResult;
import com.yeungzhy.yeed.common.core.security.LoginUserHelper;
import com.yeungzhy.yeed.gateway.security.MenuCache;
import com.yeungzhy.yeed.gateway.security.MenuCacheSnapshot;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

/**
 * Sa-Token 权限认证 配置类
 *
 * <p>网关是唯一的鉴权点：登录态与接口权限都在这里判定，下游服务不再各自校验，
 * 因此本类的拒绝是 fail-closed 的：权限缓存不可用时只放行超管
 *
 * @author yeungzhy
 * @since 2026-08-15
 */
@Slf4j
@Configuration
public class SaTokenConfig {

    @Resource
    private PermitAllProperties permitAllProperties;

    @Resource
    private MenuCache menuCache;


    /**
     * Sa-Token 全局过滤器：拦截全部请求，鉴权失败交由 {@link #doOnAuthError} 写响应
     *
     * @return 过滤器实例；favicon 直接排除，不进鉴权逻辑
     * @author yeungzhy
     * @since 2026-08-15
     */
    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
            .addInclude("/**")
            .addExclude("/favicon.ico")
            .setAuth(obj -> doAuth())
            .setError(this::doOnAuthError);
    }


    /**
     * 接口权限鉴权（动态）：按请求路径查「接口路径 → 权限码」本地权限快照
     *
     * <p>鉴权规则：
     * <ul>
     *   <li>命中放行名单（{@link PermitAllProperties}）：无需认证，直接放行
     *   <li>缓存不可用（未预热 / 外部清库 / Redis 异常）：fail-closed 拒绝，仅 SUPER_ADMIN 放行，
     *       保证权限被改坏时超管仍能进系统修复
     *   <li>命中登记：先精确匹配（O(1) 内存查），miss 后对通配登记做 Ant 匹配兜底（带路径参数的接口），
     *       全部在 {@link MenuCacheSnapshot#lookupPerms(String)} 本地完成，无 Redis 往返
     *   <li>精确与模式均未命中：路径没登记过，按 404 处理而不是放行
     * </ul>
     *
     * <p>本方法跑在 Netty event loop 线程上（SaReactorFilter 的 auth 回调）。权限快照查表全程本地、
     * 零 Redis；仅存的阻塞点是 登录态校验与读取会话权限仍需同步读 Sa-Token 会话（会话在 Redis，JWT 为 Simple 模式）
     *
     * @author yeungzhy
     * @since 2026-08-15
     */
    public void doAuth() {
        String path = SaHolder.getRequest().getRequestPath();

        // 放行名单（无需认证即可访问）：命中直接放行，跳过登录校验与权限校验
        if (SaRouter.isMatch(permitAllProperties.getPaths(), path)) {
            log.info("命中放行名单，直接放行：path={}", path);
            return;
        }

        // 非放行名单：先校验登录态（未登录在此抛 NotLoginException → 401）
        StpUtil.checkLogin();

        // 本地权限快照不可用（未初始化/Redis 异常）fail-closed，仅超管放行
        MenuCacheSnapshot snapshot = menuCache.getSnapshot();
        if (snapshot == null) {
            if (LoginUserHelper.isSuperAdmin()) {
                log.error("菜单接口权限缓存不可用，SUPER_ADMIN 放行：path={}", path);
                return;
            }
            // 安全关键事件：在源头打 error 日志，handleAuthError 侧仅 warn 补充
            log.error("菜单接口权限缓存不可用，拒绝访问：path={}", path);
            throw new NotPermissionException("获取权限失败，系统鉴权暂时不可用，请联系管理员");
        }

        // 先精确匹配，miss 后 Ant 模式匹配（带路径参数接口），全部本地完成
        String perm = snapshot.lookupPerms(path);

        // 精确与 Ant 模式均未命中：快照是系统全量登记接口，路径不在其中即 404
        if (perm == null) {
            throw new NotFoundException("Not Found");
        }

        // 校验当前账号是否含有本次请求 path 的权限
        StpUtil.checkPermission(perm);
    }


    /**
     * 处理鉴权错误：按异常类型映射 HTTP 状态码，并写出 ApiResult 形态的错误响应
     *
     * <p>401 / 403 / 404 属预期拒绝，HTTP 码与 body 业务码同源，前端按状态码分流即可，
     * 其余异常统一 500 + SYSTEM_ERROR，与前三类的分工刻意不同：网关层故障没有领域语义可表达
     *
     * @param e 鉴权过程中抛出的异常，可为 null
     * @return 含错误码与话术的结果对象，恒不为 null
     * @author yeungzhy
     * @since 2026-08-15
     */
    public SaResult doOnAuthError(Throwable e) {
        ServerWebExchange exchange = SaReactorSyncHolder.getExchange();
        RequestPath requestPath = exchange.getRequest().getPath();
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        /*
         * 预期中的业务拒绝（未登录/无权限）：warn 级、不打印堆栈，避免刷屏淹没真实故障
         * 真异常才 error + 堆栈（缓存不可用的 fail-closed 已在上游单独打 error 日志）
         *
         * 同时设置真实 HTTP 状态码
         */
        switch (e) {
            case NotLoginException ignored -> {
                log.warn("接口未登录被拒 [{}]", requestPath);
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return new SaResult()
                        .set(ApiResult.Fields.code, ApiResult.CommonCode.UNAUTHORIZED.getCode())
                        .set(ApiResult.Fields.msg, ApiResult.CommonCode.UNAUTHORIZED.getMsg())
                        .set(ApiResult.Fields.data, null);
            }
            case NotFoundException ignored -> {
                log.warn("接口未找到被拒 [{}]", requestPath);
                response.setStatusCode(HttpStatus.NOT_FOUND);
                return new SaResult()
                        .set(ApiResult.Fields.code, ApiResult.CommonCode.NOT_FOUND.getCode())
                        .set(ApiResult.Fields.msg, ApiResult.CommonCode.NOT_FOUND.getMsg())
                        .set(ApiResult.Fields.data, null);
            }
            case NotPermissionException ignored -> {
                log.warn("接口无权限被拒 [{}]", requestPath);
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return new SaResult()
                        .set(ApiResult.Fields.code, ApiResult.CommonCode.FORBIDDEN.getCode())
                        .set(ApiResult.Fields.msg, ApiResult.CommonCode.FORBIDDEN.getMsg())
                        .set(ApiResult.Fields.data, null);
            }
            case null, default -> {
                // 真异常：HTTP 500 为通用稳定语义（网关层错误信号），body 用领域语义 SYSTEM_ERROR(1000)，
                // 与上方 401/403/404「无领域语义、body 对齐 HTTP」是两种分工，刻意不对齐
                log.error("接口权限认证异常 [{}]", requestPath, e);
                response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                return new SaResult()
                        .set(ApiResult.Fields.code, ApiResult.CommonCode.SYSTEM_ERROR.getCode())
                        .set(ApiResult.Fields.msg, ApiResult.CommonCode.SYSTEM_ERROR.getMsg())
                        .set(ApiResult.Fields.data, null);
            }
        }
    }


}
