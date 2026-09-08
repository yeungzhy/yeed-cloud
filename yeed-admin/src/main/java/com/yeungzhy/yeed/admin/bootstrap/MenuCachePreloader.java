package com.yeungzhy.yeed.admin.bootstrap;

import com.yeungzhy.yeed.admin.sys.menu.service.MenuCacheReloader;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 菜单缓存预热器（每次启动必做）
 *
 * <p>网关两块缓存的数据源都在本服务的菜单表，启动时重建一次，避免清库后网关鉴权长期失效：
 * <ol>
 *   <li>「接口路径 → 权限码」Map：接口鉴权
 *   <li>「请求路径 → 祖链名称」Map：操作日志
 * </ol>
 *
 * @author yeungzhy
 * @since 2026-08-15
 */
@Slf4j
@Component
public class MenuCachePreloader implements ApplicationRunner {

    @Resource
    private MenuCacheReloader menuCacheReloader;

    /**
     * 启动时重建一次菜单缓存
     *
     * <p>Redis 不可用时不阻断启动：RedisHelper 已把异常收敛为 false，缓存缺失由
     * {@link MenuCacheReloader#reloadIfAbsent()} 在首个菜单树请求时补建
     *
     * @param args 启动参数，本预热器不使用
     */
    @Override
    public void run(ApplicationArguments args) {
        menuCacheReloader.reload();
    }

}
