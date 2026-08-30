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
 * <p> 1.网关按「接口路径 → 权限码」权限Map 做接口鉴权
 * <p> 2.网关按「请求路径 → 祖链名称」祖链Map 做操作日志记录
 *
 * @author yeungzhy
 * @since 2026-08-15
 */
@Slf4j
@Component
public class MenuCachePreloader implements ApplicationRunner {

    @Resource
    private MenuCacheReloader menuCacheReloader;

    @Override
    public void run(ApplicationArguments args) {
        menuCacheReloader.reload();
    }

}
