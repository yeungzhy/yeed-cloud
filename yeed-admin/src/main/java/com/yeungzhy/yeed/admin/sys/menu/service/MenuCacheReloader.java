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
 * <p>网关鉴权与操作日志所需的两块缓存都源自本服务的菜单表，全量重建是唯一写入口：
 * 任何一处菜单写操作都调 {@link #reload()}，不做增量更新
 *
 * @author yeungzhy
 * @since 2026-08-28
 */
@Slf4j
@Component
public class MenuCacheReloader {
    /** 祖链描述的分隔符，与菜单 path 的 {@code /} 保持一致 */
    private static final String PATH_SEPARATOR = "/";
    /** 单层通配符 */
    private static final char SINGLE_WILDCARD  = '*';


    @Resource
    private SysMenuMapper sysMenuMapper;
    @Resource
    private RedisHelper redisHelper;


    /**
     * 全量重建菜单缓存并广播变更
     *
     * <p>启动预热与菜单写操作共用的唯一重建入口；按路径是否含通配符拆成两组，各写两个 Map：
     * <ul>
     *   <li>精确接口：路径 → 权限码、路径 → 祖链描述
     *   <li>通配接口：同上，供网关按 Ant 模式匹配
     * </ul>
     *
     * <p>副作用是整体覆盖写 Redis 并发布变更事件，不读旧值、不做增量，
     * 发布失败不重试，由网关本地缓存 TTL 兜底
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
         * 生命周期完全由本类管理；初始化判据只认 ALL 键
         * 显式永久存储（timeout=null）：本缓存每次整体重建、无历史残留，
         *     且网关 fail-closed 依赖 ALL 键存在性判定，若随默认 TTL 过期将误判而拒绝所有请求
         */
        redisHelper.setMap(CacheConstant.MENU_PERMS_EXACT, exactApiPermMap, null);
        redisHelper.setMap(CacheConstant.MENU_DESC_EXACT, exactApiDescMap, null);
        redisHelper.setMap(CacheConstant.MENU_PERMS_ANT, antApiPermMap, null);
        redisHelper.setMap(CacheConstant.MENU_DESC_ANT, antApiDescMap, null);

        /*
         * 发布变更事件：网关本地缓存（GatewayApiPermsCache）订阅后即时失效，权限变更精准生效，
         * 发布失败有网关本地缓存 TTL 自愈兜底，最坏延迟几分钟生效
         */
        redisHelper.publish(CacheConstant.MENU_CACHE_CHANGED, String.valueOf(System.currentTimeMillis()));
        log.info("菜单缓存已重建并发布变更事件：精确接口=权限{},描述{}，动态接口=权限{},描述{}",
                exactApiPermMap.size(), exactApiDescMap.size(), antApiPermMap.size(), antApiDescMap.size());
    }


    /**
     * 拼出菜单的祖链描述（操作日志用）
     *
     * @param menu    起点菜单，不能为 null
     * @param byIdMap 全量菜单 id → 菜单映射；缺 id 视为断链并停止上溯
     * @return 以 {@code /} 连接的祖链名称，至少包含起点自身名称
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
