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
     */
    public static final String SYS_MENU_API_PERMS_ALL = "yeed:admin:menu:api-perms:all";

}
