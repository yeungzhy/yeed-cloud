package com.yeungzhy.yeed.common.data.mybatis;

import com.yeungzhy.yeed.common.core.crypto.AesUtil;
import com.yeungzhy.yeed.common.core.crypto.CipherEnvelope;
import com.yeungzhy.yeed.common.core.crypto.Crypto;
import com.yeungzhy.yeed.common.core.crypto.CryptoProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MyBatis 字段加解密拦截器
 *
 * <p> 职责：拦截 MyBatis 写入（{@link ParameterHandler#setParameters}）与读取
 * （{@link ResultSetHandler#handleResultSets}）两条链路，对实体上标注 {@link Crypto} 的字段自动 AES
 * 加解密。Java 层 entity 字段永远是明文，DB 层永远是密文，业务代码不感知密文
 *
 * <p> 覆盖场景（完整递归）：单 entity、{@code @Param} 多参数 / {@code foreach} 批量、
 * 批量集合（saveBatch）、DTO 包装类嵌套 entity、MyBatis-Plus JSON 列（JacksonTypeHandler）内部字段。
 * 时序是成立的：写发生在 TypeHandler 序列化之前，读发生在反序列化之后
 *
 * <p> 防重入：入库值统一套 {@link CipherEnvelope} 的 {@code ENC(...)} 信封，已带信封的值跳过加密、
 * 解密也只认带信封的值。即便 {@code setParameters} 被外部工具（SQL 日志 agent 为打印带参 SQL 而
 * mock 调用）重复触发，也不会把上一轮密文再加密一次。业务代码禁止再手动调用
 * {@link AesUtil#encrypt} / {@link AesUtil#decrypt}，双重加密的数据解不回来
 *
 * <p> 递归防御：双向关联（如 {@code User.dept} ↔ {@code Department.manager}）会无限递归，
 * 用基于对象身份的 {@link IdentityHashMap} 判重断开环（每条 SQL 新建、不跨请求共享）；
 * 非循环的深嵌套由 {@link #MAX_DEPTH} 截断
 *
 * <p> 异常分级，两条链路刻意不对称：
 * <ul>
 *   <li>加密失败抛 {@link RuntimeException} 让事务回滚，绝不允许明文落库
 *   <li>解密失败记 error 日志并把字段降级为 null，不让一条脏数据把整页列表打成 500
 * </ul>
 *
 * <p> 注册：不标 {@code @Component}（业务模块扫不到 yeed-common 包），由
 * {@link com.yeungzhy.yeed.common.data.config.MybatisPlusAutoConfiguration} 以 {@code @Bean} 注册，
 * MyBatis-Plus 自动收集容器中的 {@link Interceptor} Bean 注入 SqlSessionFactory
 *
 * @author YangZhaoHuang
 * @author yeungzhy
 * @since 2026-05-22
 */
@Slf4j
@Intercepts({
        @Signature(type = ParameterHandler.class, method = "setParameters", args = {java.sql.PreparedStatement.class}),
        @Signature(type = ResultSetHandler.class, method = "handleResultSets", args = {java.sql.Statement.class})
})
public class FieldCryptoInterceptor implements Interceptor {

    /**
     * 递归最大深度，兜底防异常数据结构把栈打爆
     *
     * <p> 加密字段嵌套超过 5 层几乎不存在；如确有需要可改为配置项
     */
    private static final int MAX_DEPTH = 5;

    /** AES 加解密配置，密钥来源；构造器注入，不在本类硬编码 */
    private final CryptoProperties cryptoProperties;

    /**
     * 类 → 该类（含父类继承链）中标注 {@link Crypto} 的字段列表，反射扫描只做一次
     *
     * <p> 空列表同样入缓存，下次直接跳过
     */
    private static final ConcurrentHashMap<Class<?>, List<Field>> CACHED_CRYPTO_FIELDS = new ConcurrentHashMap<>();

    /**
     * 类 → 该类（含父类继承链）中需要递归下探的非基本类型字段
     *
     * <p> 缓存是为了跳过每次递归都反射一遍 getDeclaredFields 的开销
     */
    private static final ConcurrentHashMap<Class<?>, List<Field>> CACHED_RECURSIVE_FIELDS = new ConcurrentHashMap<>();


    /**
     * 构造拦截器
     *
     * @param cryptoProperties AES 配置，提供密钥，不能为 null
     */
    public FieldCryptoInterceptor(CryptoProperties cryptoProperties) {
        this.cryptoProperties = cryptoProperties;
    }


    /**
     * {@inheritDoc}
     *
     * <p> 写链路在 proceed 之前处理参数、读链路在 proceed 之后处理结果集：
     * 两侧都要等字段变成对象形态才能递归，早一步是 TypeHandler 未介入的原始值，晚一步已经落库
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object target = invocation.getTarget();

        if (target instanceof ParameterHandler parameterHandler) {
            MetaObject metaObject = SystemMetaObject.forObject(parameterHandler.getParameterObject());
            Object parameterObject = metaObject.getOriginalObject();
            // visited 每条 SQL 新建，不跨请求共享，无需同步
            Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            handleObject(parameterObject, true, visited, 0);
        }

        Object result = invocation.proceed();

        if (target instanceof ResultSetHandler) {
            Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            if (result instanceof Collection<?> items) {
                for (Object item : items) {
                    handleObject(item, false, visited, 0);
                }
            } else {
                handleObject(result, false, visited, 0);
            }
        }

        return result;
    }


    // ========================= 核心递归处理 =========================

    /**
     * 递归处理对象，对标注 {@link Crypto} 的 String 字段执行加/解密，并递归进嵌套对象寻找更多 @Crypto 字段
     *
     * @param obj       待处理对象（entity / DTO 包装类 / Map / Collection），可为 null
     * @param isEncrypt true=加密（入库前），false=解密（出库后）
     * @param visited   已访问对象集合（基于对象身份，防循环引用），调用方每条 SQL 新建
     * @param depth     当前递归深度，超过 {@link #MAX_DEPTH} 强制截断
     */
    private void handleObject(Object obj, boolean isEncrypt, Set<Object> visited, int depth) {
        if (obj == null || depth > MAX_DEPTH) return;

        Class<?> clazz = obj.getClass();
        if (isSimpleType(clazz)) return;
        /*
         * 命名模块类型（java.time.LocalDateTime、java.util.Date 等）内部不可能有 @Crypto，
         * 且 JDK 17+ 禁止反射其私有字段，setAccessible 会抛 InaccessibleObjectException
         * 容器型虽同属命名模块，但要留下来遍历内部元素，故只跳过非容器型
         */
        if (isInternalType(clazz) && !Map.class.isAssignableFrom(clazz) && !Collection.class.isAssignableFrom(clazz)) return;

        /*
         * 用 IdentityHashMap（按 == 判等）而非 HashSet（按 equals 判等）：
         * 两个内容相同的 String / List 不该被当成同一个对象而漏掉其中一个
         */
        if (!visited.add(obj)) return;

        /*
         * ParamMap 是 @Param 多参数 / foreach 批量的参数形态，values 才是真实 entity
         * 业务 Map 的 value 类型不可预测，遍历开销靠 visited 与 MAX_DEPTH 兜住
         */
        if (obj instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                handleObject(value, isEncrypt, visited, depth + 1);
            }
            return;
        }

        if (obj instanceof Collection<?> coll) {
            for (Object item : coll) {
                handleObject(item, isEncrypt, visited, depth + 1);
            }
            return;
        }

        List<Field> cryptoFields = getCryptoFields(clazz);
        for (Field field : cryptoFields) {
            cryptoField(field, obj, isEncrypt);
        }

        List<Field> recursiveFields = getRecursiveFields(clazz);
        for (Field field : recursiveFields) {
            try {
                handleObject(field.get(obj), isEncrypt, visited, depth + 1);
            } catch (IllegalAccessException e) {
                log.error("递归访问字段失败 [field={}, class={}]", field.getName(), clazz.getName(), e);
            }
        }
    }


    /**
     * 对单个 {@link Crypto} 字段执行加/解密
     *
     * <p> 异常分级：加密失败抛 {@link RuntimeException} 让事务回滚，绝不能让明文落库；
     * 解密失败只记 error 日志并把字段置 null，不让一条脏数据把整页列表打成 500
     *
     * @param field     已 setAccessible 的 {@link Crypto} 字段
     * @param obj       字段所属对象，不能为 null
     * @param isEncrypt true=加密，false=解密
     */
    private void cryptoField(Field field, Object obj, boolean isEncrypt) {
        try {
            Object value = field.get(obj);
            // 仅处理 String 类型且非空的字段值（AES 加解密约定输入输出均为 String）
            if (!(value instanceof String strValue) || strValue.isEmpty()) return;

            String key = cryptoProperties.getAes().getKey();
            String resultValue;
            if (isEncrypt) {
                // 幂等：已带密文信封（ENC(...)）的值视为已加密，直接跳过，防止 setParameters 被重复触发
                if (CipherEnvelope.isWrapped(strValue)) return;
                resultValue = CipherEnvelope.wrap(AesUtil.encrypt(key, strValue));
            } else {
                // 仅解密带信封的密文；明文 / 历史裸密文原样返回，不尝试解密
                if (!CipherEnvelope.isWrapped(strValue)) return;
                resultValue = AesUtil.decrypt(key, CipherEnvelope.unwrap(strValue));
            }

            // 值未变化时不写回，减少反射开销
            if (!strValue.equals(resultValue)) {
                field.set(obj, resultValue);
            }
        } catch (Exception e) {
            if (isEncrypt) {
                // 加密失败：抛出，事务回滚，绝不明文落库
                throw new RuntimeException("Field encryption failed [field=" + field.getName()
                        + ", class=" + obj.getClass().getName() + "]", e);
            }
            // 解密失败：降级为 null，记录日志，不拖垮整页列表
            log.error("字段解密失败，降级为 null [field={}, class={}]", field.getName(), obj.getClass().getName(), e);
            try {
                field.set(obj, null);
            } catch (IllegalAccessException ignored) {
                // 降级失败也无法处理，仅记录，不中断主流程
            }
        }
    }


    // ========================= 缓存与辅助方法 =========================

    /**
     * 取类（含父类继承链）中标注 {@link Crypto} 的字段列表（带缓存）
     *
     * <p> 沿继承链扫描，父类上标的 @Crypto 同样生效
     *
     * @param clazz 目标类，不能为 null
     * @return @Crypto 字段，无则返回空列表
     */
    private List<Field> getCryptoFields(Class<?> clazz) {
        return CACHED_CRYPTO_FIELDS.computeIfAbsent(clazz, c -> {
            List<Field> fields = new ArrayList<>();
            Class<?> current = c;
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    if (field.isAnnotationPresent(Crypto.class)) {
                        field.setAccessible(true);
                        fields.add(field);
                    }
                }
                current = current.getSuperclass();
            }
            return fields;
        });
    }

    /**
     * 取类（含父类继承链）中需要递归下探的非基本类型字段（带缓存）
     *
     * <p> 排除四类：static（类共享，不参与实例加解密）、transient（不参与序列化）、
     * 基本类型与 String（不可能含 @Crypto）、命名模块类型（见 {@link #isInternalType(Class)}）
     *
     * @param clazz 目标类，不能为 null
     * @return 待递归的字段，无则返回空列表
     */
    private List<Field> getRecursiveFields(Class<?> clazz) {
        return CACHED_RECURSIVE_FIELDS.computeIfAbsent(clazz, c -> {
            List<Field> fields = new ArrayList<>();
            Class<?> current = c;
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    int mod = field.getModifiers();
                    if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) continue;
                    Class<?> fieldType = field.getType();
                    if (isSimpleType(fieldType)) continue;
                    // 递归进 LocalDateTime 这类 JDK 对象会遍历其内部字段，触发 setAccessible 的
                    // InaccessibleObjectException
                    if (isInternalType(fieldType)) continue;
                    field.setAccessible(true);
                    fields.add(field);
                }
                current = current.getSuperclass();
            }
            return fields;
        });
    }

    /**
     * 是否为基本类型、包装类或 String，这些类型既不递归也绝不含 @Crypto 字段
     *
     * <p> Map / Collection 不算简单类型，它们在 {@link #handleObject} 里单独分支遍历
     *
     * @param clazz 待判定的类型，不能为 null
     * @return true 无需下探
     */
    private boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz.equals(String.class)
                || Number.class.isAssignableFrom(clazz)
                || Boolean.class.isAssignableFrom(clazz)
                || Character.class.isAssignableFrom(clazz);
    }

    /**
     * 是否为 JDK / 第三方库的命名模块类型
     *
     * <p> 这类类型（{@code java.time.LocalDateTime}、{@code java.util.Date}、{@code java.math.BigDecimal} 等）
     * 内部不可能标注 {@link Crypto}，且 JDK 17+ 强模块系统禁止反射其私有字段，{@code setAccessible}
     * 会抛 {@code InaccessibleObjectException}；用户自定义类属于 unnamed module，照常递归
     *
     * <p> {@code java.util.HashMap} 等容器型同属命名模块，但它们走 {@link #handleObject} 的
     * Map / Collection 分支，入口判断已把容器型排除在外
     *
     * @param clazz 待判定的类型，不能为 null
     * @return true 应跳过反射
     */
    private boolean isInternalType(Class<?> clazz) {
        return clazz.getModule().isNamed();
    }
}
