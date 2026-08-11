package com.yeungzhy.yeed.common.data.request;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.yeungzhy.yeed.common.data.model.BaseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 排序字段白名单基类
 *
 * <h3>设计思路（为什么用 Builder / Consumer）</h3>
 * <p>父类构造器执行 <b>早于</b> 子类字段初始化，所以不能用「子类构造器里 super() 之后再调 add()」——
 * 那样父类的 sorts 在 super() 结束时还是空的，子类后续 add() 进去的内容无法保证在 freeze 之前完成。
 * 因此必须在 super() 调用的<b>参数位置</b>把白名单一次性传完。
 *
 * <h3>子类两种写法（任选其一，效果完全相同）</h3>
 * <p><b>写法 A：Consumer lambda（紧凑，推荐 4~8 个字段以内）</b>
 * <pre>{@code
 * @Component
 * public class SysUserSorts extends BaseSorts<SysUser> {
 *     public SysUserSorts() {
 *         super(SysUser.class, b -> b
 *                 .add(SysUser.Fields.username,   SysUser::getUsername)
 *                 .add(BaseEntity.Fields.createTime, SysUser::getCreateTime));
 *     }
 * }}</pre>
 *
 * <p><b>写法 B：显式 Builder（更直观，字段多时更清晰）</b>
 * <pre>{@code
 * @Component
 * public class SysUserSorts extends BaseSorts<SysUser> {
 *     public SysUserSorts() {
 *         super(SysUser.class, new BaseSorts.Builder<SysUser>()
 *                 .add(SysUser.Fields.username,   SysUser::getUsername)
 *                 .add(BaseEntity.Fields.createTime, SysUser::getCreateTime)
 *                 .add(SysUser.Fields.status,     SysUser::getStatus));
 *     }
 * }}</pre>
 *
 * <h3>安全策略</h3>
 * <p>未知字段静默忽略（不抛异常、不打 error 日志），避免攻击者通过响应差异枚举白名单。
 *
 * <h3>默认排序方向</h3>
 * <p>isAsc 为 null → <b>降序</b>（业务惯例：最新记录在前）
 *
 * <h3>为什么不用 static 工具类</h3>
 * <p>白名单虽不可变，但做成实例化 Bean 而非 static 工具类，原因有三：
 * <ul>
 *   <li><b>泛型绑定</b>：{@code BaseSorts<T extends BaseEntity>} 的泛型上下文需要实例化才能体现；
 *       static 方法无法携带泛型类型参数，也无法让 {@link #apply} 直接接收带类型的 {@code LambdaQueryWrapper<T>}。</li>
 *   <li><b>依赖注入</b>：子类以 {@code @Component} 注册后可被 Service 层注入复用；
 *       未来如需按角色/租户动态裁剪白名单或注入配置类，Bean 天然支持依赖注入，static 做不到。</li>
 *   <li><b>可测试性</b>：作为 Bean 可在测试中用 {@code @MockBean} 替换或注入自定义白名单子类，
 *       比直接 {@code new} 或 static 更灵活。</li>
 * </ul>
 */
public abstract class BaseSorts<T extends BaseEntity> {

    private final Class<T> entityClass;
    private final Map<String, SFunction<T, ?>> sorts;

    /* ============ 两个构造重载，任选其一一调用 ============ */

    /**
     * 构造器 - Consumer lambda 形式（紧凑写法）
     * <p> 例：{@code super(SysUser.class, b -> b.add("x", X::getX).add("y", X::getY)); }
     */
    protected BaseSorts(Class<T> entityClass, Consumer<Builder<T>> config) {
        this.entityClass = entityClass;
        Builder<T> b = new Builder<>();
        config.accept(b);
        this.sorts = Map.copyOf(b.sorts);
    }

    /**
     * 构造器 - 显式 Builder 形式（更直观）
     * <p> 例：{@code super(SysUser.class, new BaseSorts.Builder<SysUser>().add(...).add(...)); }
     */
    protected BaseSorts(Class<T> entityClass, Builder<T> builder) {
        this.entityClass = entityClass;
        this.sorts = Map.copyOf(builder.sorts);
    }

    /* ============ Builder ============ */

    public static final class Builder<T extends BaseEntity> {
        private final Map<String, SFunction<T, ?>> sorts = new LinkedHashMap<>();

        public Builder<T> add(String field, SFunction<T, ?> ref) {
            sorts.put(field, ref);
            return this;
        }
    }

    /* ============ apply 系列方法 ============ */

    /**
     * 应用单个排序字段。
     *
     * @param field 前端传入的字段名（Java 属性名，驼峰）；null 或不在白名单 → 静默忽略
     * @param isAsc 升序标志；<b>null 默认降序</b>（业务惯例：最新记录在前）
     */
    public final void apply(LambdaQueryWrapper<T> w, String field, Boolean isAsc) {
        if (field == null) {
            return;
        }
        SFunction<T, ?> ref = sorts.get(field);
        if (ref == null) {
            // 静默忽略未知字段，不泄露白名单内容
            return;
        }
        // null isAsc 默认降序：只有显式传 isAsc=TRUE 时才升序
        w.orderBy(true, Boolean.TRUE.equals(isAsc), ref);
    }

    /**
     * 应用多字段排序（兼容 PageRequest 的「单字段 + 多字段」两套接口）。
     * <p> 执行顺序：<b>先单字段 → 再多字段（列表顺序）</b>，符合 SQL ORDER BY a, b, c 的直觉。
     * <p> isAsc 为 null 或 orders 内 isAsc 为 null → <b>默认降序</b>。
     *
     * @param orderField 单字段（便捷接口，可 null）
     * @param isAsc      单字段方向（可 null）
     * @param orders     多字段列表（可 null / 空）
     */
    public final void applyAll(LambdaQueryWrapper<T> w,
                               String orderField,
                               Boolean isAsc,
                               List<com.yeungzhy.yeed.common.core.request.PageRequest.OrderItem> orders) {
        // 1. 先应用单字段
        apply(w, orderField, isAsc);

        // 2. 再按顺序应用多字段
        if (orders == null || orders.isEmpty()) {
            return;
        }
        for (com.yeungzhy.yeed.common.core.request.PageRequest.OrderItem item : orders) {
            if (item == null) {
                continue;
            }
            apply(w, item.getField(), item.getIsAsc());
        }
    }

    public final Class<T> entityClass() {
        return entityClass;
    }

    /**
     * 返回白名单字段的只读视图（仅用于调试 / 文档生成）
     */
    public final List<String> allowedFields() {
        return List.copyOf(sorts.keySet());
    }
}
