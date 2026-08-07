package com.yeungzhy.yeed.common.mybatis;

import com.yeungzhy.yeed.common.crypto.AesUtil;
import com.yeungzhy.yeed.common.crypto.Crypto;
import com.yeungzhy.yeed.common.crypto.CryptoProperties;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MyBatis 字段加解密拦截器
 *
 * <p> <b>职责</b>：拦截 MyBatis 写入（{@link ParameterHandler#setParameters}）与读取
 * （{@link ResultSetHandler#handleResultSets}）两条链路，对实体上标注 {@link Crypto} 的字段
 * 自动执行 AES 加解密，使业务代码不再感知密文——Java 层 entity 字段<b>永远是明文</b>，
 * DB 层<b>永远是密文</b>。
 *
 * <p> <b>覆盖场景</b>（完整递归）：
 * <ul>
 *   <li>单 entity 参数：{@code insert(SysUser entity)}</li>
 *   <li>MyBatis {@code @Param} 多参数 / {@code foreach} 批量：parameterObject 是 {@code MapperMethod.ParamMap}，
 *       递归遍历 values 找到真实 entity</li>
 *   <li>批量集合：{@code saveBatch(List)} 或自定义 {@code int insertAll(List<SysUser>)}</li>
 *   <li><b>DTO 包装类参数</b>：{@code insert(UserRequestDTO dto)}，dto 内嵌套 entity，
 *       递归进 POJO 字段找到嵌套的 @Crypto 字段</li>
 *   <li><b>MyBatis-Plus JSON 字段嵌 @Crypto</b>：entity 的 JSON 列（{@code @TableField(typeHandler=JacksonTypeHandler)}）
 *       在 Java 层是对象，其内部 @Crypto 字段会被递归处理。写时 Interceptor 在 TypeHandler 序列化<b>之前</b>
 *       加密；读时 Interceptor 在 TypeHandler 反序列化<b>之后</b>解密，时序均成立</li>
 * </ul>
 *
 * <p> <b>循环引用防御</b>：双向关联（如 {@code User.dept} ↔ {@code Department.manager}）会导致无限递归。
 * 用基于对象身份的 {@link IdentityHashMap} visited set 在进入前判重，断开环。每条 SQL 调用新建一个 visited，
 * 不跨请求共享，线程安全。
 *
 * <p> <b>深度兜底</b>：非循环的深嵌套（理论上存在但实际罕见）由 {@link #MAX_DEPTH} 强制截断，防止异常数据结构拖垮栈。
 *
 * <p> <b>异常分级</b>：
 * <ul>
 *   <li>加密失败：抛 {@link RuntimeException}，事务回滚——<b>绝不允许明文落库</b></li>
 *   <li>解密失败：记录 error 日志 + 字段降级为 null——<b>不让一条脏数据把整页列表 500</b></li>
 * </ul>
 *
 * <p> <b>启用约定（防重入）</b>：启用本拦截器后，业务代码（Service/Mapper 调用方）
 * <b>禁止</b>再手动调用 {@link AesUtil#encrypt} / {@link AesUtil#decrypt}，否则会双重加密导致数据无法解回。
 * 防重入依靠"删干净手动调用 + CR"，不加运行时判定，零运行时开销。
 *
 * <p> <b>注册</b>：本类不标 {@code @Component}（yeed-common 是 starter，业务模块的
 * {@code @SpringBootApplication} 扫不到 {@code com.yeungzhy.yeed.common.*} 包），改由
 * {@code MybatisPlusConfig} 以 {@code @Bean} 方式注册。MyBatis-Plus 会自动收集容器中的
 * {@link Interceptor} Bean 注入到所有 {@code SqlSessionFactory}。
 *
 * @author YangZhaoHuang at 2026-05-22 10:17
 * @author yeungzhy at 2026-08-07 重构：去硬编码 key、完整递归、循环引用防御、异常分级
 */
@Slf4j
@Intercepts({
        @Signature(type = ParameterHandler.class, method = "setParameters", args = {java.sql.PreparedStatement.class}),
        @Signature(type = ResultSetHandler.class, method = "handleResultSets", args = {java.sql.Statement.class})
})
public class FieldCryptoInterceptor implements Interceptor {

    /**
     * 递归最大深度（兜底，防止异常数据结构导致栈溢出）
     * <p> 加密字段嵌套超过 5 层几乎不存在；如确有需要可改为配置项
     */
    private static final int MAX_DEPTH = 5;

    /** AES 加解密配置（构造器注入，避免硬编码 key） */
    private final CryptoProperties cryptoProperties;

    /**
     * 类 → 该类（含父类继承链）中标注 {@link Crypto} 的字段列表（带缓存，反射扫描只做一次）
     * <p> 空列表表示该类无 @Crypto 字段，下次直接跳过
     */
    private static final ConcurrentHashMap<Class<?>, List<Field>> CACHED_CRYPTO_FIELDS = new ConcurrentHashMap<>();

    /**
     * 类 → 该类（含父类继承链）中需要递归处理的非基本类型字段（带缓存）
     * <p> 用于递归进 POJO 嵌套字段寻找 @Crypto，避免每次递归都反射 getDeclaredFields
     * <p> 跳过 static / transient / 基本类型字段
     */
    private static final ConcurrentHashMap<Class<?>, List<Field>> CACHED_RECURSIVE_FIELDS = new ConcurrentHashMap<>();


    public FieldCryptoInterceptor(CryptoProperties cryptoProperties) {
        this.cryptoProperties = cryptoProperties;
    }


    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object target = invocation.getTarget();

        // 1. 入库加密：拦截 ParameterHandler.setParameters，在 proceed 之前处理参数对象
        //    此时参数对象尚未被 TypeHandler 处理，POJO 字段还是对象形态，可递归加密
        if (target instanceof ParameterHandler parameterHandler) {
            MetaObject metaObject = SystemMetaObject.forObject(parameterHandler.getParameterObject());
            Object parameterObject = metaObject.getOriginalObject();
            // 每条 SQL 调用新建 visited set，不跨请求共享，线程安全
            Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            handleObject(parameterObject, true, visited, 0);
        }

        // 2. 执行原方法（写库或读库）
        Object result = invocation.proceed();

        // 3. 出库解密：拦截 ResultSetHandler.handleResultSets，在 proceed 之后处理结果集
        //    此时 TypeHandler（如 JacksonTypeHandler）已完成反序列化，POJO 字段已是对象形态，可递归解密
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
     * @param obj       待处理对象（entity / DTO 包装类 / Map / Collection）
     * @param isEncrypt true=加密（入库前），false=解密（出库后）
     * @param visited   已访问对象集合（基于对象身份，防循环引用）
     * @param depth     当前递归深度，超过 {@link #MAX_DEPTH} 强制截断
     */
    private void handleObject(Object obj, boolean isEncrypt, Set<Object> visited, int depth) {
        if (obj == null || depth > MAX_DEPTH) return;

        Class<?> clazz = obj.getClass();
        // 基本类型 / 包装类 / String：绝不含 @Crypto 字段，直接跳过
        if (isSimpleType(clazz)) return;
        // JDK / 第三方命名模块类型（如 java.time.LocalDateTime、java.util.Date）：
        // 内部不可能有 @Crypto 字段，且 JDK 17+ 强模块系统禁止反射其私有字段（setAccessible 抛
        // InaccessibleObjectException）。但 Map / Collection 需保留（ParamMap 等需遍历 values），
        // 仅跳过非容器型的命名模块类型
        if (isInternalType(clazz) && !Map.class.isAssignableFrom(clazz) && !Collection.class.isAssignableFrom(clazz)) return;

        // 循环引用防御核心：visited.add 返回 false 表示对象已处理过，立即跳过断开环
        // 用 IdentityHashMap（按 == 判等）而非 HashSet（按 equals 判等）：
        // 避免 String/List 的值比较引发误判，且 O(1) 查询
        if (!visited.add(obj)) return;

        // 分支1：Map（覆盖 MyBatis 的 MapperMethod.ParamMap + 业务 Map）
        //   ParamMap 是 @Param 多参数 / foreach 批量的参数形态，values 是真实 entity
        //   业务 Map 的 value 类型不可预测，但有 visited + maxDepth 防御，遍历开销可接受
        if (obj instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                handleObject(value, isEncrypt, visited, depth + 1);
            }
            return;
        }

        // 分支2：Collection（覆盖 List 参数、saveBatch、自定义批量 insert）
        if (obj instanceof Collection<?> coll) {
            for (Object item : coll) {
                handleObject(item, isEncrypt, visited, depth + 1);
            }
            return;
        }

        // 分支3：POJO（entity / DTO 包装类）
        //   先处理本类 @Crypto 字段（加/解密），再递归进非基本类型字段找嵌套的 @Crypto
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
     * <p> <b>异常分级</b>：
     * <ul>
     *   <li>加密失败：抛 {@link RuntimeException} 让事务回滚——绝不能让明文落库</li>
     *   <li>解密失败：记 error 日志 + 字段降级为 null——不让一条脏数据把整页列表 500</li>
     * </ul>
     *
     * @param field     字段（已 setAccessible）
     * @param obj       字段所属对象
     * @param isEncrypt true=加密，false=解密
     */
    private void cryptoField(Field field, Object obj, boolean isEncrypt) {
        try {
            Object value = field.get(obj);
            // 仅处理 String 类型且非空的字段值（AES 加解密约定输入输出均为 String）
            if (!(value instanceof String strValue) || strValue.isEmpty()) return;

            String key = cryptoProperties.getAes().getKey();
            String resultValue = isEncrypt
                    ? AesUtil.encrypt(key, strValue)
                    : AesUtil.decrypt(key, strValue);

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
     * 获取类（含父类继承链）中标注 {@link Crypto} 的字段列表（带缓存）
     * <p> 遍历继承链以支持父类标注 @Crypto 的场景（如 BaseEntity 未来加 @Crypto 字段）
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
     * 获取类（含父类继承链）中需要递归处理的非基本类型字段（带缓存）
     * <p> 跳过 static / transient / 基本类型 / 包装类 / String 字段：
     * <ul>
     *   <li>static：类共享，不参与实例加解密</li>
     *   <li>transient：不参与序列化，加密它无意义</li>
     *   <li>基本类型 / String：绝不含 @Crypto 字段，无需递归</li>
     * </ul>
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
                    // 跳过 JDK / 第三方命名模块类型字段（如 BaseEntity.createTime 是 LocalDateTime）：
                    // 递归进 LocalDateTime 实例会遍历其内部字段，触发 setAccessible InaccessibleObjectException
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
     * 判断是否为基本类型、包装类或 String——这些类型绝不递归，也绝不含 @Crypto 字段
     * <p> 注意：Map / Collection 不在此列，它们在 {@link #handleObject} 中单独分支处理
     */
    private boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz.equals(String.class)
                || Number.class.isAssignableFrom(clazz)
                || Boolean.class.isAssignableFrom(clazz)
                || Character.class.isAssignableFrom(clazz);
    }

    /**
     * 判断是否为 JDK / 第三方库的命名模块类型
     * <p> 命名模块（如 {@code java.base}）的类（{@code java.time.LocalDateTime}、{@code java.util.Date}、
     * {@code java.math.BigDecimal} 等）：
     * <ul>
     *   <li>内部不可能标注 {@link Crypto}（用户无法给 JDK 类加注解）</li>
     *   <li>JDK 17+ 强模块系统禁止反射其私有字段，{@code setAccessible} 抛
     *       {@code InaccessibleObjectException}</li>
     * </ul>
     * <p> 用户自定义类属于 unnamed module（{@code isNamed()=false}），正常递归。
     * <p> 注意：{@code java.util.HashMap} 等容器型也是命名模块，但它们在 {@link #handleObject}
     * 的 Map / Collection 分支单独处理，入口判断已排除容器型。
     */
    private boolean isInternalType(Class<?> clazz) {
        return clazz.getModule().isNamed();
    }
}
