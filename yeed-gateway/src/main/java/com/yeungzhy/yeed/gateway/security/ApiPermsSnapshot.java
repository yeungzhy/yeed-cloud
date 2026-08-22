package com.yeungzhy.yeed.gateway.security;

import cn.dev33.satoken.router.SaRouter;

import java.util.Map;

/**
 * 菜单接口权限快照（网关本地缓存的不可变值）
 *
 * <p>由 {@link ApiPermsCache} 从 Redis 回源加载后生成，一次请求内多次鉴权
 * （登录 + 权限校验）共用同一份内存副本，避免对 Redis 的重复远程调用：
 * <ul>
 *     <li>{@code exactPerms}：精确接口映射（ALL 键），路径按原样匹配，O(1) 命中；</li>
 *     <li>{@code antPatternPerms}：含通配符的动态接口映射（ANT 键），精确 miss 后
 *     按 Ant 模式匹配兜底（如登记 {@code /yeed-admin/sys/user/}{@code *}{@code /avatar.svg}）。</li>
 * </ul>
 *
 * <p>与 Redis 中的两个键保持一一对应、整体重建；构造即防御性拷贝不可变，
 * 避免外部修改影响缓存一致性。类型判断（精确/通配）只在写入侧做一次，读取侧零判断。
 *
 * @author yeungzhy
 * @since 2026-08-15
 */
public class ApiPermsSnapshot {

    private final Map<String, String> exactPerms;

    private final Map<String, String> antPatternPerms;

    public ApiPermsSnapshot(Map<String, String> exactPerms, Map<String, String> antPatternPerms) {
        this.exactPerms = Map.copyOf(exactPerms == null ? Map.of() : exactPerms);
        this.antPatternPerms = Map.copyOf(antPatternPerms == null ? Map.of() : antPatternPerms);
    }

    /**
     * 按请求路径查权限码：先精确匹配，miss 后 Ant 模式匹配兜底
     *
     * @param path 真实请求路径（含网关前缀，如 {@code /yeed-admin/sys/user/page}）
     * @return 命中的权限码；未命中返回 null
     */
    public String lookup(String path) {
        String perm = exactPerms.get(path);
        if (perm != null) {
            return perm;
        }
        for (Map.Entry<String, String> entry : antPatternPerms.entrySet()) {
            if (SaRouter.isMatch(entry.getKey(), path)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
