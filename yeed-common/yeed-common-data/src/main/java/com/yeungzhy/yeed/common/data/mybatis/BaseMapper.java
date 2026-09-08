package com.yeungzhy.yeed.common.data.mybatis;

import com.yeungzhy.yeed.common.core.request.PageRequest;
import com.yeungzhy.yeed.common.core.result.PageResult;
import com.yeungzhy.yeed.common.core.security.LoginUserHelper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.injector.methods.AlwaysUpdateSomeColumnById;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yeungzhy.yeed.common.data.support.MybatisPageConverters;
import org.apache.ibatis.annotations.Param;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 项目统一 Mapper 基类：在 MyBatis-Plus {@link com.baomidou.mybatisplus.core.mapper.BaseMapper} 之上集中提供项目扩展能力
 *
 * <p> 扩展方法全部由 {@link CustomSqlInjector} 注入，SQL 由 injector 包下同名的 {@code AbstractMethod} 拼装，
 * 按生效条件分三组：
 * <ul>
 *     <li>全表可用：{@link #existsByWrapper(Wrapper)} 系列存在性判断、
 *         {@link #alwaysUpdateSomeColumnById(Object)} 全字段更新、
 *         {@link #selectPageResult(PageRequest, Wrapper, Function)} 系列分页查询
 *     <li>需 {@code @TableLogic} 且实体含 {@code deleteBy} 字段：
 *         {@link #logicDeleteById(Long, long, Long)} 系列逻辑删除
 *     <li>需 {@code @TableLogic}：{@link #physicalDeleteById(Serializable)} 等逻辑删除逃逸方法，
 *         支撑“回收站分页 → 恢复 / 彻底删除”
 * </ul>
 *
 * <p> 未满足生效条件的表不会注入对应方法，调用抛 {@code BindingException}，不要跨条件误用
 *
 * <p> 业务 Mapper 直接继承本接口即可获得全部能力：
 * <pre>{@code public interface SysUserMapper extends BaseMapper<SysUser> {}}</pre>
 *
 * @author yeungzhy
 * @since 2026-08-08
 * @see CustomSqlInjector
 */
public interface BaseMapper<T> extends com.baomidou.mybatisplus.core.mapper.BaseMapper<T> {

    // ===== 存在性判断：由 ExistsByWrapper 注入，全表可用 =====

    /**
     * 根据 Wrapper 条件判断是否存在记录
     *
     * <p> 生成 {@code SELECT EXISTS (SELECT 1 FROM t WHERE ...)}：命中首条记录即短路返回，
     * 相比 {@code COUNT(*)} 不必扫描全部匹配行
     *
     * @param queryWrapper 查询条件；传 null 查全表，WHERE 自动追加 {@code @TableLogic} 未删除条件，
     *                     已删记录不会被判为存在
     * @return true 存在；false 不存在
     * @since 2026-08-08
     */
    Boolean existsByWrapper(@Param(Constants.WRAPPER) Wrapper<T> queryWrapper);


    /**
     * 根据 Consumer 装配的条件判断是否存在记录
     *
     * <p> {@link #existsByWrapper(Wrapper)} 的 Lambda 便捷入口，多字段、模糊等复杂条件统一走本方法
     *
     * @param consumer 条件装配器，在函数内调用 {@code eq} / {@code like} 等方法装配查询条件，不能为 null
     * @return true 存在；false 不存在
     * @since 2026-08-08
     */
    default Boolean existsByCondition(Consumer<LambdaQueryWrapper<T>> consumer) {
        LambdaQueryWrapper<T> lambdaQuery = Wrappers.lambdaQuery();
        consumer.accept(lambdaQuery);
        return existsByWrapper(lambdaQuery);
    }


    /**
     * 根据单个字段等值判断是否存在记录
     *
     * <p> {@link #existsByCondition(Consumer)} 的单字段特化，适合“用户名 / 邮箱是否已占用”这类唯一性校验；
     * 多字段、模糊等条件请用 {@link #existsByCondition(Consumer)}
     *
     * <p> value 为 null 时 MyBatis-Plus 会拼 {@code IS NULL}，唯一性校验请先判空再调，
     * 否则「没填手机号」会被判成「手机号已占用」
     *
     * @param column 实体属性的方法引用，如 {@code SysUser::getUsername}，不能为 null
     * @param value  待比较的值，可为 null（等值语义为 {@code IS NULL}）
     * @return true 存在；false 不存在
     * @since 2026-08-08
     */
    default Boolean existsByColumn(SFunction<T, ?> column, Object value) {
        return existsByCondition(lambdaQuery -> lambdaQuery.eq(column, value));
    }


    // ===== 全字段更新：由 MyBatis-Plus 官方 AlwaysUpdateSomeColumnById 注入，全表可用 =====

    /**
     * 根据主键更新全部字段（含值为 null 的字段）
     *
     * <p> 由 MyBatis-Plus 官方 {@link AlwaysUpdateSomeColumnById} 注入：SET 子句无条件包含除主键外的全部列、
     * 不跳过 null，用于 {@code updateById} 做不到的“把指定列置空”；WHERE 自动追加 {@code @TableLogic} 未删除条件
     *
     * <p> 务必先 {@code selectById} 读出完整实体再改后回写：用新建的空实体直接调用，未赋值的列会被原样置 NULL
     *
     * @param entity 待更新实体，必须携带主键值；主键为 null 时更新不到任何行
     * @return 更新行数；0 表示主键不存在或已被逻辑删除
     * @since 2026-08-08
     */
    int alwaysUpdateSomeColumnById(@Param(Constants.ENTITY) T entity);


    // ===== 逻辑删除：需 @TableLogic 且实体含 deleteBy 字段的表 =====

    /**
     * 生成纳秒级 epoch 时间戳，用作逻辑删除标识
     *
     * <p> 不用 MySQL {@code UNIX_TIMESTAMP()} 作删除标记：其精度只到秒，高并发下同秒删除会撞唯一约束。
     * 纳秒取自 {@link Instant#getNano()}，实际精度取决于 JDK 与操作系统（JDK 9+ 通常到微秒级），仍远高于秒级；
     * 跨机器时钟漂移由数据库唯一约束兜底
     *
     * @return 纳秒级 epoch 时间戳
     * @since 2026-08-08
     */
    default long nanoEpoch() {
        Instant now = Instant.now();
        return now.getEpochSecond() * 1_000_000_000L + now.getNano();
    }


    /**
     * 根据主键逻辑删除（逻辑删除列置为删除时间戳，同时记录删除人）
     *
     * <p> WHERE 携带“未删除”条件，重复删除返回 0（天然幂等）；删除时间戳与删除人均由调用方传入，
     * 业务侧统一走自动填充入口 {@link #deleteByIdAutoFill(Long)}
     *
     * <pre>{@code UPDATE yeed_sys_user SET delete_time = ?, delete_by = ? WHERE id = ? AND delete_time = 0}</pre>
     *
     * @param id         主键，不能为 null
     * @param deleteTime 删除时间戳，用 {@link #nanoEpoch()} 生成；不得为 0，0 是“未删除”标记
     * @param deleteBy   删除人 ID，可为 null；系统操作等无登录态场景显式传 {@code 0L}
     * @return 1 删除成功；0 主键不存在或已被删除
     * @since 2026-08-08
     */
    int logicDeleteById(@Param("id") Long id,
                        @Param("deleteTime") long deleteTime,
                        @Param("deleteBy") Long deleteBy);


    /**
     * 根据主键逻辑删除（自动填充纳秒级删除时间戳与当前登录删除人）
     *
     * <p> {@link #logicDeleteById} 的自动填充入口：时间戳取 {@link #nanoEpoch()}，删除人取当前登录用户 ID。
     * 逻辑删除统一走本方法，不要调框架内置的 {@code deleteById}：内置实现把删除标记交给数据库函数，精度只到秒
     *
     * <p> 删除人取 {@link LoginUserHelper#getUserId()}（强登录语义），定时任务等无登录态场景会抛
     * {@link IllegalStateException}，请改调 {@link #logicDeleteById} 显式传 deleteBy
     *
     * @param id 主键，不能为 null
     * @return true 删除成功；false 主键不存在或已被删除
     * @since 2026-08-08
     */
    default boolean deleteByIdAutoFill(Long id) {
        return logicDeleteById(id, nanoEpoch(), LoginUserHelper.getUserId()) > 0;
    }


    /**
     * 根据主键集合批量逻辑删除（逻辑删除列置为删除时间戳，同时记录删除人）
     *
     * <p> WHERE 携带“未删除”条件，不存在或已删除的 id 自动跳过（天然幂等）；
     * 删除时间戳与删除人均由调用方传入，业务侧统一走自动填充入口 {@link #deleteByIdsAutoFill(Collection)}
     *
     * <pre>{@code UPDATE yeed_sys_user SET delete_time = ?, delete_by = ? WHERE id IN (?, ?) AND delete_time = 0}</pre>
     *
     * <p> ids 为空集合时 foreach 会生成 {@code IN ()} 造成 SQL 语法错误，本方法刻意不防御，调用方必须保证非空
     *
     * @param ids        主键集合，不能为 null 或空集合
     * @param deleteTime 删除时间戳，用 {@link #nanoEpoch()} 生成，整批共用一个值
     * @param deleteBy   删除人 ID；系统操作等无登录态场景显式传 {@code 0L}
     * @return 实际删除行数，可能小于 ids 规模（部分主键不存在或已被删除）
     * @since 2026-08-08
     */
    int logicDeleteByIds(@Param("ids") Collection<Long> ids,
                         @Param("deleteTime") long deleteTime,
                         @Param("deleteBy") Long deleteBy);


    /**
     * 根据主键集合批量逻辑删除（自动填充纳秒级删除时间戳与当前登录删除人）
     *
     * <p> {@link #logicDeleteByIds} 的自动填充入口：空集合直接返回 0 不触库；
     * 超过 {@link Constants#DEFAULT_BATCH_SIZE}（1000）条按片切分执行，避免 IN 列表过长，
     * 各片共用同一个删除时间戳与删除人（整批视为一次删除）
     *
     * <p> 各片是独立 UPDATE，需要整体原子性请在 Service 层加事务；
     * 删除人取 {@link LoginUserHelper#getUserId()}（强登录语义），无登录态场景请改调 {@link #logicDeleteByIds}
     *
     * @param ids 主键集合；null 或空集合返回 0
     * @return 实际删除行数，可能小于 ids 规模（部分主键不存在或已被删除）
     * @since 2026-08-08
     */
    default int deleteByIdsAutoFill(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        long deleteTime = nanoEpoch();
        Long deleteBy = LoginUserHelper.getUserId();
        if (ids.size() <= Constants.DEFAULT_BATCH_SIZE) {
            return logicDeleteByIds(ids, deleteTime, deleteBy);
        }
        List<Long> idList = ids instanceof List ? (List<Long>) ids : new ArrayList<>(ids);
        int total = 0;
        for (int i = 0; i < idList.size(); i += Constants.DEFAULT_BATCH_SIZE) {
            total += logicDeleteByIds(
                    idList.subList(i, Math.min(i + Constants.DEFAULT_BATCH_SIZE, idList.size())), deleteTime, deleteBy);
        }
        return total;
    }


    // ===== 逻辑删除逃逸：需 @TableLogic 表，支撑“回收站”场景 =====

    /*
     * 开启全局逻辑删除后，MyBatis-Plus 所有内置方法都会自动追加“未删除”条件，
     * 由此产生三个死区：查不到已删数据、恢复不了已删数据、无法物理清除；本组方法即为打通这三个死区
     */

    /**
     * 根据主键物理删除（真 DELETE，不带“未删除”条件，不可恢复）
     *
     * <p> 逻辑删除表上 MyBatis-Plus 内置的 {@code deleteById} 会被改写成 UPDATE，
     * 本方法注入真正的 DELETE，用于回收站彻底删除
     *
     * @param id 主键，不能为 null
     * @return 删除行数；0 表示主键不存在
     * @since 2026-08-08
     */
    int physicalDeleteById(Serializable id);


    /**
     * 根据 Wrapper 条件物理删除（真 DELETE，不带“未删除”条件，不可恢复）
     *
     * <p> Wrapper 未携带有效条件时兜底 {@code WHERE 1=0}，一条都不删，防误删全表；
     * 条件请全部通过 Wrapper 方法构建：Wrapper 上设置的 entity 条件不生效
     *
     * @param wrapper 删除条件，可为 null（此时一条都不删）
     * @return 删除行数；0 表示无匹配记录
     * @since 2026-08-08
     */
    int physicalDelete(@Param(Constants.WRAPPER) Wrapper<T> wrapper);


    /**
     * 根据 Wrapper 条件查询列表（包含已逻辑删除的数据）
     *
     * <p> WHERE 不带“未删除”条件，Wrapper 为空时查全表（含已删），用于回收站列表与已删数据审计；
     * 条件请全部通过 Wrapper 方法构建：Wrapper 上设置的 entity 条件不生效
     *
     * @param wrapper 查询条件，传 null 查全表（含已删）
     * @return 实体列表，包含已逻辑删除的数据；无匹配时为空列表
     * @since 2026-08-08
     */
    List<T> selectListWithDeleted(@Param(Constants.WRAPPER) Wrapper<T> wrapper);


    /**
     * 根据 Wrapper 条件分页查询（包含已逻辑删除的数据）
     *
     * <p> 分页由 {@link PaginationInnerInterceptor} 依据 {@link IPage} 参数自动完成；
     * 业务侧一般不直接调用，统一走 {@link #selectPageResultWithDeleted(PageRequest, Wrapper, Function)} 便捷入口
     *
     * @param page         分页参数（current / size），不能为 null；同一个对象同时承载返回的记录与总数
     * @param queryWrapper 查询条件，传 null 查全表（含已删）；Wrapper 上设置的 entity 条件不生效
     * @return 分页结果，包含已逻辑删除的数据
     * @since 2026-08-13
     */
    IPage<T> selectPageWithDeleted(IPage<T> page, @Param(Constants.WRAPPER) Wrapper<T> queryWrapper);


    /**
     * 根据主键恢复已逻辑删除的数据（逻辑删除列置回未删除值，删除人一并置 NULL）
     *
     * <p> WHERE 携带“已删除”条件，对未删除的数据执行返回 0（天然幂等）；
     * 实体含 {@code deleteBy} 字段时一并置 NULL，避免恢复后残留上次删除痕迹。
     * 不做自动填充：需要记录恢复人 / 恢复时间，由业务层在恢复后自行更新
     *
     * @param id 主键，不能为 null
     * @return 1 恢复成功；0 主键不存在或数据未被删除
     * @since 2026-08-08
     */
    int restoreById(Serializable id);


    // ===== 分页查询：全表可用。PageRequest / PageResult 是 common-core 纯 POJO，业务侧不接触 ORM 分页类型 =====

    /**
     * 分页查询并把实体逐条映射成目标类型（一行完成“入参 → Page → 查询 → PageResult”）
     *
     * <p> 目标类型由 {@code mapper} 决定：通常是 VO，也可以是 DTO 或实体本身。
     * 方法名指向返回类型（{@code PageResult}）而非目标类型，正是为了不把调用方钉死在 VO 上
     *
     * <p> 始终基于 {@code selectPage} 的返回对象组装结果，不会误用入参里携带的旧分页数据；
     * 无需元素转换时用 {@link #selectPageResult(PageRequest, Wrapper)}
     *
     * @param pageRequest  分页请求（pageNum / pageSize），不能为 null，支持 {@link PageRequest} 子类（如 XxxPageDTO）
     * @param queryWrapper 查询条件；传 null 查全表
     * @param mapper       实体到目标类型的转换函数，不能为 null，通常传生成器产出的 {@code XxxConvert::toVO}
     * @param <V>          目标类型
     * @return 分页结果，records 已逐条转换为目标类型；无命中时为空页而非 null
     * @since 2026-08-12
     */
    default <V> PageResult<V> selectPageResult(PageRequest pageRequest, Wrapper<T> queryWrapper, Function<T, V> mapper) {
        Page<T> page = MybatisPageConverters.toMybatisPlusPage(pageRequest);
        return MybatisPageConverters.toPageResult(selectPage(page, queryWrapper), mapper);
    }


    /**
     * 分页查询并返回实体分页结果（无需元素转换时的便捷入口）
     *
     * <p> 等价于 {@code selectPageResult(pageRequest, queryWrapper, Function.identity())}；
     * 需要转换元素请用 {@link #selectPageResult(PageRequest, Wrapper, Function)}
     *
     * @param pageRequest  分页请求（pageNum / pageSize），不能为 null，支持 {@link PageRequest} 子类（如 XxxPageDTO）
     * @param queryWrapper 查询条件；传 null 查全表
     * @return 分页结果，records 为实体列表
     * @since 2026-08-12
     */
    default PageResult<T> selectPageResult(PageRequest pageRequest, Wrapper<T> queryWrapper) {
        return selectPageResult(pageRequest, queryWrapper, Function.identity());
    }


    /**
     * 按页取数并逐条映射成目标类型，不做 count（一行完成“入参 → Slice → 查询 → List”）
     *
     * <p> 与 {@link #selectPageResult(PageRequest, Wrapper, Function)} 的差异是少了 {@code SELECT COUNT(*)}：
     * 只发一条带 LIMIT 的查询。适用「总数已在循环外查得、顺序翻页取数」的场景（导出 / 跑批 / 全量同步），
     * 逐页再各查一遍总数是纯浪费
     *
     * <p> 返回 {@code List} 而非 {@link PageResult} 是刻意的设计：没有 count 就没有可信的 total，
     * 把它包进 PageResult 只会带出一个恒为 0 的假值；总数请用 {@code selectCount} 之类单独查
     *
     * @param pageRequest  分页请求（pageNum / pageSize），不能为 null，支持 {@link PageRequest} 子类（如 XxxPageDTO）
     * @param queryWrapper 查询条件；传 null 查全表
     * @param mapper       实体到目标类型的转换函数，不能为 null，通常传生成器产出的 {@code XxxConvert::toVO}
     * @param <V>          目标类型
     * @return 本页记录，已逐条转换为目标类型；无数据时为空列表（不为 null）
     * @since 2026-09-07
     */
    default <V> List<V> selectPageRecords(PageRequest pageRequest, Wrapper<T> queryWrapper, Function<T, V> mapper) {
        Page<T> page = MybatisPageConverters.toMybatisPlusSlice(pageRequest);
        return MybatisPageConverters.toRecords(selectPage(page, queryWrapper), mapper);
    }


    /**
     * 分页查询（包含已逻辑删除的数据）并把实体逐条映射成目标类型
     *
     * <p> 与 {@link #selectPageResult(PageRequest, Wrapper, Function)} 的唯一差异是不带“未删除”过滤，
     * 用于回收站分页列表；仅 {@code @TableLogic} 表可用，否则对应方法未注入，调用抛 {@code BindingException}
     *
     * @param pageRequest  分页请求（pageNum / pageSize），不能为 null，支持 {@link PageRequest} 子类（如 XxxPageDTO）
     * @param queryWrapper 查询条件；传 null 查全表（含已删）
     * @param mapper       实体到目标类型的转换函数，不能为 null，通常传生成器产出的 {@code XxxConvert::toVO}
     * @param <V>          目标类型
     * @return 分页结果，records 已逐条转换为目标类型，包含已逻辑删除的数据
     * @since 2026-08-13
     */
    default <V> PageResult<V> selectPageResultWithDeleted(PageRequest pageRequest, Wrapper<T> queryWrapper, Function<T, V> mapper) {
        Page<T> page = MybatisPageConverters.toMybatisPlusPage(pageRequest);
        return MybatisPageConverters.toPageResult(selectPageWithDeleted(page, queryWrapper), mapper);
    }

}

