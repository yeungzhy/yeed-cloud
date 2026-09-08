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
 * <p> 白名单必须在 {@code super(...)} 的实参位置一次性传完：父类构造器先于子类字段初始化执行，
 * 等 super() 返回再 add() 就赶不上不可变快照的构建
 *
 * <p> 未知字段静默忽略，不抛异常也不打 error 日志，避免攻击者靠响应差异枚举白名单
 *
 * <p> 排序方向 isAsc 为 null 时取降序，业务惯例是最新记录在前
 *
 * <p> 做成实例化 Bean 而非 static 工具类：泛型 {@code T} 要绑到 {@link #apply} 的
 * {@code LambdaQueryWrapper<T>} 上，且子类注册成 Bean 后可被注入、可被 {@code @MockBean} 替换
 *
 * @author yeungzhy
 * @since 2026-08-08
 */
public abstract class BaseSorts<T extends BaseEntity> {

    private final Class<T> entityClass;
    private final Map<String, SFunction<T, ?>> sorts;

    // --------- 两个构造重载，子类任选其一 ---------

    /**
     * 构造白名单，用 Consumer lambda 逐个登记字段
     *
     * @param entityClass 实体类型，不能为 null
     * @param config      白名单装配器，不能为 null；形如 {@code b -> b.add("username", SysUser::getUsername)}
     */
    protected BaseSorts(Class<T> entityClass, Consumer<Builder<T>> config) {
        this.entityClass = entityClass;
        Builder<T> b = new Builder<>();
        config.accept(b);
        this.sorts = Map.copyOf(b.sorts);
    }

    /**
     * 构造白名单，用显式 {@link Builder} 登记字段
     *
     * @param entityClass 实体类型，不能为 null
     * @param builder     装好字段的 Builder，不能为 null
     */
    protected BaseSorts(Class<T> entityClass, Builder<T> builder) {
        this.entityClass = entityClass;
        this.sorts = Map.copyOf(builder.sorts);
    }

    // --------- Builder ---------

    /**
     * 排序白名单装配器，登记顺序即后续 {@code ORDER BY} 的优先级
     *
     * @param <T> 实体类型
     */
    public static final class Builder<T extends BaseEntity> {

        private final Map<String, SFunction<T, ?>> sorts = new LinkedHashMap<>();

        /**
         * 登记一个可排序字段
         *
         * @param field 前端传入的字段名（Java 属性名，驼峰），不能为 null；重复登记同名会被后者覆盖
         * @param ref   实体属性的方法引用，不能为 null
         * @return 当前 Builder，便于链式调用
         */
        public Builder<T> add(String field, SFunction<T, ?> ref) {
            sorts.put(field, ref);
            return this;
        }
    }

    // --------- apply 系列方法 ---------

    /**
     * 应用单个排序字段
     *
     * @param w     待追加排序的查询条件，不能为 null
     * @param field 前端传入的字段名（Java 属性名，驼峰）；null 或不在白名单则静默忽略
     * @param isAsc 升序标志；null 默认降序（业务惯例：最新记录在前）
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
     * 应用多字段排序（兼容 PageRequest 的「单字段 + 多字段」两套接口）
     * <p> 执行顺序：先单字段 → 再多字段（列表顺序），符合 SQL ORDER BY a, b, c 的直觉
     * <p> isAsc 为 null 或 orders 内 isAsc 为 null → 默认降序
     *
     * @param orderField 单字段（便捷接口，可 null）
     * @param isAsc      单字段方向（可 null）
     * @param orders     多字段列表（可 null / 空）
     */
    public final void applyAll(LambdaQueryWrapper<T> w,
                               String orderField,
                               Boolean isAsc,
                               List<com.yeungzhy.yeed.common.core.request.PageRequest.OrderItem> orders) {
        apply(w, orderField, isAsc);

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

    /**
     * 取泛型绑定的实体类型
     *
     * @return 构造时传入的实体 Class
     */
    public final Class<T> entityClass() {
        return entityClass;
    }

    /**
     * 返回白名单字段的只读视图（仅用于调试 / 文档生成）
     *
     * @return 可排序字段名，按登记顺序
     */
    public final List<String> allowedFields() {
        return List.copyOf(sorts.keySet());
    }
}
