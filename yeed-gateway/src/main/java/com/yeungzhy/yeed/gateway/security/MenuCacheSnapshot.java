package com.yeungzhy.yeed.gateway.security;

import cn.dev33.satoken.router.SaRouter;

import java.util.Map;

/**
 * 菜单缓存快照（网关本地缓存的不可变值）
 *
 * <p>由 {@link MenuCache} 从 Redis 回源加载后生成，一次请求内共用同一份内存副本，
 * 避免鉴权与记日志各回源一次：
 * <ul>
 *   <li>{@code exact}：精确接口映射（ALL 键），路径按原样匹配，O(1) 命中
 *   <li>{@code ant}：含通配符的动态接口映射（ANT 键），精确 miss 后按 Ant 模式匹配兜底
 * </ul>
 *
 * <p>与 Redis 中的键一一对应、整体重建；构造即防御性拷贝不可变，避免外部修改影响缓存一致性；
 * 类型判断（精确 / 通配）只在写入侧做一次，读取侧零判断
 *
 * <p>一份快照同时承载鉴权（perms）与日志（desc），共用同一失效事件与同一退避状态
 *
 * @author yeungzhy
 * @since 2026-08-15
 */
public class MenuCacheSnapshot {

    private final Map<String, String> exactPerms;
    private final Map<String, String> exactDesc;
    private final Map<String, String> antPerms;
    private final Map<String, String> antDesc;

    public MenuCacheSnapshot(Map<String, String> exactPerms, Map<String, String> exactDesc,
                             Map<String, String> antPerms, Map<String, String> antDesc) {

        this.exactPerms = Map.copyOf(exactPerms == null ? Map.of() : exactPerms);
        this.exactDesc = Map.copyOf(exactDesc == null ? Map.of() : exactDesc);
        this.antPerms = Map.copyOf(antPerms == null ? Map.of() : antPerms);
        this.antDesc = Map.copyOf(antDesc == null ? Map.of() : antDesc);
    }

    /**
     * 按请求路径查缓存值：先精确匹配，miss 后 Ant 模式匹配兜底
     *
     * <p>抽取成共用方法是必做项而非代码洁癖：两处逻辑一旦漂移，会出现「鉴权通行但日志描述查不到」，
     * 这类不一致极难排查
     *
     * @param exact 精确映射，不能为 null
     * @param ant   通配映射，不能为 null
     * @param path  真实请求路径（含网关前缀），不能为 null
     * @return 命中的值；未命中返回 null
     */
    private static String match(Map<String, String> exact, Map<String, String> ant, String path) {
        String desc = exact.get(path);
        if (desc != null) {
            return desc;
        }
        for (Map.Entry<String, String> entry : ant.entrySet()) {
            if (SaRouter.isMatch(entry.getKey(), path)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 查接口权限码
     *
     * @param path 真实请求路径（含网关前缀），不能为 null
     * @return 权限码；未登记返回 null，由调用方按无此接口处理
     */
    public String lookupPerms(String path) {
        return match(exactPerms, antPerms, path);
    }

    /**
     * 查接口中文描述（菜单祖链名，操作日志用）
     *
     * @param path 真实请求路径（含网关前缀），不能为 null
     * @return 描述文本；未登记返回 null
     */
    public String lookupDesc(String path) {
        return match(exactDesc, antDesc, path);
    }

}
