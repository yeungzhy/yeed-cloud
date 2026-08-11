package com.yeungzhy.yeed.common.core.exception;

import java.util.Collection;
import java.util.Map;

/**
 * 业务断言工具类
 *
 * <p>与 Hutool/Spring 的通用 {@code Assert} 不同，本类所有方法在断言失败时抛出
 * {@link BizException}，其 message 会被 {@code GlobalExceptionHandler}
 * 原样透传给前端用户；而通用 Assert 抛出的 {@link IllegalArgumentException}
 * 会被统一兜底为"参数校验失败"，无法把具体业务原因（如"密码不正确"）传达给用户。
 *
 * @author yeungzhy
 * @since 2026-08-06
 */
public final class BizAssert {

    private BizAssert() {}


    /**
     * 断言表达式为真
     *
     * @param expression 待断言的表达式
     * @param message    断言失败时透传给前端的错误描述
     * @throws BizException expression 为 false 时抛出
     */
    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new BizException(message);
        }
    }

    /**
     * 断言表达式为假
     *
     * @param expression 待断言的表达式
     * @param message    断言失败时透传给前端的错误描述
     * @throws BizException expression 为 true 时抛出
     */
    public static void isFalse(boolean expression, String message) {
        if (expression) {
            throw new BizException(message);
        }
    }

    /**
     * 断言对象非空
     *
     * @param object  待断言的对象
     * @param message 断言失败时透传给前端的错误描述
     * @throws BizException object 为 null 时抛出
     */
    public static void notNull(Object object, String message) {
        if (object == null) {
            throw new BizException(message);
        }
    }

    /**
     * 断言字符串非空白（null、空串、纯空白均视为不通过）
     *
     * @param text    待断言的字符序列
     * @param message 断言失败时透传给前端的错误描述
     * @throws BizException text 为 null、空或纯空白时抛出
     */
    public static void notBlank(CharSequence text, String message) {
        if (text == null || text.toString().trim().isEmpty()) {
            throw new BizException(message);
        }
    }

    /**
     * 断言集合非空（null 与空集合均视为不通过）
     *
     * @param collection 待断言的集合
     * @param message    断言失败时透传给前端的错误描述
     * @throws BizException collection 为 null 或空时抛出
     */
    public static void notEmpty(Collection<?> collection, String message) {
        if (collection == null || collection.isEmpty()) {
            throw new BizException(message);
        }
    }

    /**
     * 断言 Map 非空（null 与空 Map 均视为不通过）
     *
     * @param map     待断言的 Map
     * @param message 断言失败时透传给前端的错误描述
     * @throws BizException map 为 null 或空时抛出
     */
    public static void notEmpty(Map<?, ?> map, String message) {
        if (map == null || map.isEmpty()) {
            throw new BizException(message);
        }
    }

}
