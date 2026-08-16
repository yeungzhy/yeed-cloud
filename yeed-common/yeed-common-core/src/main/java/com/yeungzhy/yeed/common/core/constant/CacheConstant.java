package com.yeungzhy.yeed.common.core.constant;

/**
 * Redis 键常量, 统一定义维护
 *
 * <p>命名规范（新增键必须遵守, 禁止自创前缀）:
 * <ul>
 *     <li>统一格式 {@code {app}:{module}:{entity}:{purpose}:{id}}</li>
 *     <li>首段固定应用名 {@code yeed}：一是与第三方键隔离（如 sa-token 的 {@code sa:session:*}）,
 *         二是与应用内权限编码（无前缀的 {@code sys:menu:save} 风格）天然划界, 避免缓存键被误读为权限标识;</li>
 *     <li>全小写 + 冒号分段, 禁止驼峰与下划线混合（Redis 键大小写敏感）;</li>
 *     <li>无 id 段时以 {@code all} 表达全量范围, 如 {@code yeed:admin:menu:api-perms:all}。</li>
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-08-14
 */
public final class CacheConstant {

    /**
     * 菜单接口权限缓存（Map&lt;接口路径, 权限码&gt;）
     * <p>值结构为 {@code Map<String, String>}：key 为按钮承载的调用接口路径
     * （如 {@code /sys/user/list}），value 为对应权限码（如 {@code sys:user:list}，
     * 由菜单实体 {@code pathToPerm} 派生）。网关据此按请求路径查权限码做接口鉴权；
     * admin 侧启动预热 + 菜单写操作自愈共同维护。
     * <p>仅存不含通配符的精确接口路径；与 {@link #SYS_MENU_API_PERMS_ALL_ANT} 成对维护
     * （重建/删除自愈必须同时写两个键）。本键同时承担「缓存已初始化」判据：
     * 网关 fail-closed 仅检查本键存在性，ANT 键缺失一律视为无动态接口。
     */
    public static final String SYS_MENU_API_PERMS_ALL = "yeed:admin:menu:api-perms:all";

    /**
     * 菜单接口权限缓存-动态接口（Map&lt;Ant 模式路径, 权限码&gt;）
     * <p>与 {@link #SYS_MENU_API_PERMS_ALL} 成对维护、仅存含通配符的登记
     * （归一化后的带路径参数接口，如 {@code /yeed-admin/sys/user/}{@code *}{@code /avatar.svg}），
     * 供网关在精确查找 miss 后做 Ant 模式匹配兜底。键缺失等价于「无动态接口」
     * （空集合），不代表缓存未初始化——初始化判据见 {@link #SYS_MENU_API_PERMS_ALL}。
     */
    public static final String SYS_MENU_API_PERMS_ALL_ANT = "yeed:admin:menu:api-perms:all:ant";

    /**
     * 菜单接口权限缓存变更通知主题（pub/sub，非缓存键）
     *
     * <p>admin 每次重建菜单接口权限缓存（{@code reloadPermsCache}）后向该主题发布
     * 变更事件，网关本地缓存（GatewayApiPermsCache）订阅后即时失效，实现权限变更
     * 精准生效（无需等待 TTL）。命名沿用缓存键规范前缀 {@code yeed:admin:menu:api-perms:}，
     * 末段用 {@code changed} 表达「变更事件」语义，与两个缓存键同前缀便于运维排查。
     */
    public static final String SYS_MENU_API_PERMS_CHANGED = "yeed:admin:menu:api-perms:changed";

}
