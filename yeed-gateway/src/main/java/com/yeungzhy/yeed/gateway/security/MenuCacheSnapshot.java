package com.yeungzhy.yeed.gateway.security;

import cn.dev33.satoken.router.SaRouter;

import java.util.Map;

/**
 * 菜单缓存快照（网关本地缓存的不可变值）
 *
 * <p>由 {@link MenuCache} 从 Redis 回源加载后生成，一次请求内共用同一份内存副本，避免对 Redis 的重复远程调用：
 * <ul>
 *     <li>{@code exact}：精确接口映射（ALL 键），路径按原样匹配，O(1) 命中；</li>
 *     <li>{@code ant}：含通配符的动态接口映射（ANT 键），精确 miss 后按 Ant 模式匹配兜底</li>
 * </ul>
 *
 * <p>与 Redis 中的两个键保持一一对应、整体重建；构造即防御性拷贝不可变，
 * 避免外部修改影响缓存一致性。类型判断（精确/通配）只在写入侧做一次，读取侧零判断。
 *
 * <p>一份快照同时承载鉴权(perms)与日志(desc)，共用同一失效事件与同一退避状态
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
     * 按请求路径查对应菜单缓存信息：先精确匹配，miss 后 Ant 模式匹配兜底
     *
     * @param path 真实请求路径（含网关前缀）
     * @return 命中的菜单缓存信息；未命中返回 null
     */
    private static String match(Map<String, String> exact, Map<String, String> ant, String path) {
        /*
         * 这个抽取是必做项, 不是代码洁癖
         * 两处逻辑一旦漂移，可能会出现「鉴权通行但日志描述查不到」
         * 这种不一致导致的问题极难排查
         */
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

    /** 查找权限码 */
    public String lookupPerms(String path) {
        return match(exactPerms, antPerms, path);
    }

    /** 查找描述 */
    public String lookupDesc(String path) {
        return match(exactDesc, antDesc, path);
    }

}
