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
     * 精确路径 → 权限码（Map）
     * <p>网关据此按请求路径查权限码做接口鉴权；admin 侧启动预热 + 菜单写操作自愈共同维护。
     * <p>本键同时承担「缓存已初始化」判据：网关 fail-closed 仅检查本键存在性，ANT 键缺失视为无动态接口
     */
    public static final String MENU_PERMS_EXACT = "yeed:admin:menu:perms:exact";
    /** Ant 通配路径 → 权限码（Map） */
    public static final String MENU_PERMS_ANT = "yeed:admin:menu:perms:ant";

    /** 精确路径 → 中文描述（Map） */
    public static final String MENU_DESC_EXACT = "yeed:admin:menu:desc:exact";
    /** Ant 通配路径 → 中文描述（Map） */
    public static final String MENU_DESC_ANT = "yeed:admin:menu:desc:ant";

    /**
     * 菜单缓存变更通知主题（pub/sub，非缓存键）
     *
     * <p>admin 每次重建菜单缓存（{@code reloadPermsCache}）后向该主题发布
     * 变更事件，网关本地缓存订阅后即时失效，实现菜单变更精准生效（无需等待 TTL）
     */
    public static final String MENU_CACHE_CHANGED = "yeed:admin:menu:cache:changed";

}
