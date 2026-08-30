package com.yeungzhy.yeed.admin.sys.menu.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yeungzhy.yeed.admin.sys.menu.entity.SysMenu;
import com.yeungzhy.yeed.admin.sys.menu.enums.MenuTypeEnum;
import com.yeungzhy.yeed.admin.sys.menu.mapper.SysMenuMapper;
import com.yeungzhy.yeed.common.cache.support.RedisHelper;
import com.yeungzhy.yeed.common.core.constant.CacheConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 菜单缓存重载器
 *
 * @author yeungzhy
 * @since 2026-08-28
 */
@Slf4j
@Component
public class MenuCacheReloader {
    /** 路径层级分隔符 */
    private static final String PATH_SEPARATOR = "/";
    /** 单层通配符 */
    private static final char SINGLE_WILDCARD  = '*';


    @Resource
    private SysMenuMapper sysMenuMapper;
    @Resource
    private RedisHelper redisHelper;


    /**
     * 全量重建菜单缓存
     * <p> 启动预热与菜单写操作自愈共用的唯一重建入口：查全量按钮菜单写入
     * <p> Map＜接口路径, 权限码＞
     * <p> Map＜接口路径, 祖链描述＞
     */
    public void reload() {
        List<SysMenu> menus = sysMenuMapper.selectList(Wrappers.lambdaQuery());
        Map<Long, SysMenu> byIdMap = menus.stream().collect(Collectors.toMap(SysMenu::getId, Function.identity()));

        Map<Boolean, List<SysMenu>> partition = menus.stream()
                .filter(e -> Objects.equals(MenuTypeEnum.BUTTON, e.getMenuType()) &&
                        Objects.nonNull(e.getPath()) &&
                        Objects.nonNull(e.getPerms())
                ).collect(Collectors.partitioningBy(m -> m.getPath().indexOf(SINGLE_WILDCARD) >= 0));

        Map<String, String> exactApiPermMap = partition.get(false).stream()
                .collect(Collectors.toMap(SysMenu::getPath, SysMenu::getPerms, (a, b) -> a));
        Map<String, String> exactApiDescMap = partition.get(false).stream()
                .collect(Collectors.toMap(SysMenu::getPath, m -> buildAncestorDesc(m, byIdMap), (a, b) -> a));

        Map<String, String> antApiPermMap = partition.get(true).stream()
                .collect(Collectors.toMap(SysMenu::getPath, SysMenu::getPerms, (a, b) -> a));
        Map<String, String> antApiDescMap = partition.get(true).stream()
                .collect(Collectors.toMap(SysMenu::getPath, m -> buildAncestorDesc(m, byIdMap), (a, b) -> a));

        /*
         * 生命周期完全由本类管理;
         * 初始化判据只认 ALL 键;
         * 显式永久存储（timeout=null）：本缓存每次整体重建、无历史残留；
         *     且网关 fail-closed 依赖 ALL 键存在性判定，若随默认 TTL 过期将误判而拒绝所有请求
         */
        redisHelper.setMap(CacheConstant.MENU_PERMS_EXACT, exactApiPermMap, null);
        redisHelper.setMap(CacheConstant.MENU_DESC_EXACT, exactApiDescMap, null);
        redisHelper.setMap(CacheConstant.MENU_PERMS_ANT, antApiPermMap, null);
        redisHelper.setMap(CacheConstant.MENU_DESC_ANT, antApiDescMap, null);

        /*
         * 发布变更事件：网关本地缓存（GatewayApiPermsCache）订阅后即时失效，权限变更精准生效；
         * 发布失败有网关本地缓存 TTL 自愈兜底，最坏延迟几分钟生效
         */
        redisHelper.publish(CacheConstant.MENU_CACHE_CHANGED, String.valueOf(System.currentTimeMillis()));
        log.info("菜单缓存已重建并发布变更事件：精确接口=权限{},描述{}，动态接口=权限{},描述{}",
                exactApiPermMap.size(), exactApiDescMap.size(), antApiPermMap.size(), antApiDescMap.size());
    }


    /**
     * 菜单构建祖链描述
     *
     * @param menu    菜单
     * @param byIdMap id-菜单映射
     * @return 菜单祖链描述
     */
    public String buildAncestorDesc(SysMenu menu, Map<Long, SysMenu> byIdMap) {
        Deque<String> names = new ArrayDeque<>();
        names.addFirst(menu.getMenuName());
        Set<Long> visited = new HashSet<>();
        Long cursor = menu.getParentId();
        while (cursor != null && !SysMenu.isRoot(cursor) && visited.add(cursor)) {
            SysMenu ancestor = byIdMap.get(cursor);
            if (ancestor == null) {
                break; // 脏数据断链，不阻断
            }
            names.addFirst(ancestor.getMenuName());
            cursor = ancestor.getParentId();
        }
        return String.join(PATH_SEPARATOR, names);
    }


    /**
     * 懒加载重建：缓存整体缺失时重建，存在则跳过
     * <p> 挂在菜单树查询侧做外部清库后的自愈（如超管进菜单管理页即恢复），避免依赖菜单写操作
     */
    public void reloadIfAbsent() {
        if (!redisHelper.hasKey(CacheConstant.MENU_PERMS_EXACT)) {
            reload();
        }
    }

}
