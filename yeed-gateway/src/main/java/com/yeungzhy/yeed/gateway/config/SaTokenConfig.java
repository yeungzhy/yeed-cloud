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

import java.util.Set;

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
     *   <li>命中登记：精确接口存 ALL 键、HGET O(1) 命中；miss 后遍历 ANT 键（仅含通配符登记）做 Ant 模式匹配兜底（带路径参数接口，见 {@link #matchPatternPerm}）；</li>
     *   <li>精确与模式均未命中（未登记/伪造路径）：视为无此权限。</li>
     * </ul>
     */
    public void addAuthPattern() {
        String path = SaHolder.getRequest().getRequestPath();

        // 放行名单（无需认证即可访问）：命中直接放行，跳过登录校验与权限校验
        if (SaRouter.isMatch(permitAllProperties.getPaths(), path)) {
            log.info("命中放行名单，直接放行：path={}", path);
            return;
        }

        // 非放行名单：先校验登录态（未登录在此抛 NotLoginException → 401）
        StpUtil.checkLogin();

        // 缓存整体缺失：fail-closed，仅超管放行
        if (!redisHelper.hasKey(CacheConstant.SYS_MENU_API_PERMS_ALL)) {
            if (LoginUserHelper.getRoleCodes().contains(BuiltinRoleEnum.SUPER_ADMIN.getRoleCode())) {
                log.error("菜单接口权限缓存缺失，SUPER_ADMIN 放行：path={}", path);
                return;
            }
            throw new NotPermissionException("获取权限失败，系统鉴权暂时不可用，请联系管理员");
        }

        // 先精确查找（普通接口，HGET O(1)；miss 后再 Ant 模式匹配（带路径参数接口）
        String perm = redisHelper.getMapValue(CacheConstant.SYS_MENU_API_PERMS_ALL, path);
        if (perm == null) {
            perm = matchPatternPerm(path);
        }

        // 接口未登记权限码，则说明接口 path 是新建或伪造，直接拦截
        if (perm == null) {
            throw new NotPermissionException("无此权限");
        }

        // 校验当前账号是否含有本次请求 path 的权限
        StpUtil.checkPermission(perm);
    }

    /**
     * 接口路径模式匹配兜底：对登记的含通配符路径（归一化后的带路径参数接口）做 Ant 匹配
     *
     * <p>无通配符的登记在 {@link #addAuthPattern()} 中已由精确查找（HGET）命中并返回，
     * 本方法仅服务带路径参数接口（如登记为 {@code /yeed-admin/sys/user/}{@code *}{@code /avatar.svg} 的按钮），
     * 与精确查找天然共存、互不干扰。匹配规则为 Ant 通配符：{@code *} 匹配单个不含 {@code /} 的路径段，
     * 与登记侧 {@code {id} → *} 的归一化语义对位，避免双星号跨段匹配造成的误判/漏判。
     *
     * @param path 真实请求路径（含网关前缀）
     * @return 命中的权限码；未命中返回 null
     */
    private String matchPatternPerm(String path) {
        // 仅遍历动态接口登记（ANT 键全是通配符模式、集合极小）：精确接口已由 HGET 命中；
        // 键缺失等价于「无动态接口」，初始化判据由 ALL 键单独承担（见 addAuthPattern 的 hasKey）
        Set<String> patternKeys = redisHelper.getMapKeys(CacheConstant.SYS_MENU_API_PERMS_ALL_ANT);
        for (String pattern : patternKeys) {
            if (SaRouter.isMatch(pattern, path)) {
                return redisHelper.getMapValue(CacheConstant.SYS_MENU_API_PERMS_ALL_ANT, pattern);
            }
        }
        return null;
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
