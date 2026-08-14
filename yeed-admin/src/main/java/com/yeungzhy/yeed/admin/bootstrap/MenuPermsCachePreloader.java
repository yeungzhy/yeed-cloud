package com.yeungzhy.yeed.admin.bootstrap;

import com.yeungzhy.yeed.admin.sys.menu.service.SysMenuService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 菜单接口权限缓存预热器（每次启动必做）
 *
 * <p>网关按「接口路径 → 权限码」Map 做接口鉴权，admin 侧在启动阶段把菜单按钮权限点
 * 全量载入 Redis 缓存，避免网关在应用就绪前的首个请求因缓存缺失被 fail-closed 拒绝。
 *
 * @author yeungzhy
 * @since 2026-08-15
 */
@Slf4j
@Component
public class MenuPermsCachePreloader implements ApplicationRunner {

    @Resource
    private SysMenuService sysMenuService;

    @Override
    public void run(ApplicationArguments args) {
        sysMenuService.reloadPermsCache();
    }

}
