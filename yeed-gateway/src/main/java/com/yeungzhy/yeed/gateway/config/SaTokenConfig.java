package com.yeungzhy.yeed.gateway.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.yeungzhy.yeed.common.cache.support.RedisHelper;
import com.yeungzhy.yeed.common.core.constant.CacheConstant;
import com.yeungzhy.yeed.common.core.enums.BuiltinRoleEnum;
import com.yeungzhy.yeed.common.core.security.LoginUserHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;

/**
 * Sa-Token 权限认证 配置类
 */
@Slf4j
@Configuration
public class SaTokenConfig {

    @Resource
    private RedisHelper redisHelper;

    @Resource
    private PermitAllProperties permitAllProperties;


    // 注册 Sa-Token全局过滤器
    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
            // 拦截地址 
            .addInclude("/**")
            // 开放地址 
            .addExclude("/favicon.ico")
            // 鉴权方法：每次访问进入
            .setAuth(obj -> addAuthPattern())
            // 异常处理方法：每次setAuth函数出现异常时进入 
            .setError(this::handleAuthError);
    }


    /**
     * 接口权限鉴权（动态）：按请求路径查「接口路径 → 权限码」缓存 Map
     *
     * <p>鉴权规则：
     * <ul>
     *   <li>命中放行名单（{@link PermitAllProperties}）：无需认证，直接放行，跳过登录校验与权限校验；</li>
     *   <li>缓存整体缺失（未预热/外部清库/Redis 异常）：fail-closed 拒绝，仅 SUPER_ADMIN 放行，且保证权限配置被改坏时超管仍可进系统修复；</li>
     *   <li>接口未登记：视为无此权限。</li>
     *   <li>命中登记（Map 含当前路径）：校验会话权限码</li>
     * </ul>
     */
    public void addAuthPattern() {
        String path = SaHolder.getRequest().getRequestPath();

        // 放行名单（无需认证即可访问）：命中直接放行，跳过登录校验与权限校验
        if (SaRouter.isMatch(permitAllProperties.getPaths(), path)) {
            log.info("命中放行名单，直接放行：path={}", path);
            return;
        }

        // 既不是白名单，也不是特殊请求头，检验当前会话是否已经登录
        StpUtil.checkLogin();

        // 缓存整体缺失：fail-closed，仅超管放行
        if (!redisHelper.hasKey(CacheConstant.SYS_MENU_API_PERMS_ALL)) {
            if (LoginUserHelper.getRoleCodes().contains(BuiltinRoleEnum.SUPER_ADMIN.getRoleCode())) {
                log.error("菜单接口权限缓存缺失，SUPER_ADMIN 放行：path={}", path);
                return;
            }
            throw new NotPermissionException("获取权限失败，系统鉴权暂时不可用，请联系管理员");
        }

        // 获取菜单权限
        String perm = redisHelper.getMapValue(CacheConstant.SYS_MENU_API_PERMS_ALL, path);

        // 接口未登记权限码, 则说明接口 path 是新建,或者伪造, 直接拦截
        if (perm == null) {
            throw new NotPermissionException("无此权限");
        }

        // 校验当前账号是否含有本次请求path的权限
        StpUtil.checkPermission(perm);
    }


    /**
     * 处理鉴权错误
     *
     * @param e 鉴权过程中抛出的异常
     * @return SaResult 包含错误信息的结果对象
     */
    public SaResult handleAuthError(Throwable e) {
        ServerWebExchange exchange = SaReactorSyncHolder.getExchange();
        exchange.getResponse().getHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        log.error("接口 [{}] 权限认证失败", exchange.getRequest().getPath(), e);

        if (e instanceof NotLoginException) {
            return new SaResult(401, "未登录，请登录", null);
        } else if (e instanceof NotPermissionException) {
            return new SaResult(403, "无此权限", null);
        } else {
            return new SaResult(500, "登录状态失效，请重新登录", null);
        }
    }


}
