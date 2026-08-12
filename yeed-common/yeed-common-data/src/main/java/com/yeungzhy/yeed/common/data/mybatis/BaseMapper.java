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
import com.yeungzhy.yeed.common.data.mybatis.injector.*;
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
 * 项目统一 Mapper：在 MyBatis-Plus {@link com.baomidou.mybatisplus.core.mapper.BaseMapper} 之上集中提供项目扩展能力
 * <p>能力总览（各方法的实现细节、亮点与注意事项见方法自身的 Javadoc）:
 * <ul>
 *     <li>存在性判断: {@code existsByWrapper}/{@code existsByCondition}/{@code existsByColumn}, 所有表可用</li>
 *     <li>全字段更新: {@code alwaysUpdateSomeColumnById}, 所有表可用</li>
 *     <li>逻辑删除: {@code deleteByIdAutoFill}/{@code deleteByIdsAutoFill},
 *         仅 @TableLogic 且实体含 deleteBy 字段的表可用</li>
 *     <li>逻辑删除逃逸: {@code physicalDeleteById}/{@code physicalDelete}/{@code selectListWithDeleted}/
 *         {@code selectPageWithDeleted}/{@code restoreById},
 *         仅 @TableLogic 表可用, 支撑"回收站分页 + 恢复 + 彻底删除"场景</li>
 *     <li>分页查询: {@code selectPageVO}/{@code selectPageResult}/{@code selectPageVOWithDeleted}, 所有表可用,
 *         一行完成"入参→IPage→查询→PageResult", 可选 Entity→VO 元素转换; WithDeleted 变体不滤已删数据</li>
 * </ul>
 * <p>用法: 业务 Mapper 继承本接口即可, 例如:
 * {@code public interface SysUserMapper extends BaseMapper<SysUser> {}}
 *
 * <p>注意:
 * <p>未满足可用条件的表不会注入对应方法, 调用将抛 BindingException, 请勿误用;
 * <p>本接口及其注入方法耦合 MyBatis-Plus, 若未来更换 ORM 需整体替换本接口实现;
 *
 * @author yeungzhy
 * @since 2026-08-08
 */
public interface BaseMapper<T> extends com.baomidou.mybatisplus.core.mapper.BaseMapper<T> {

    // ==================================================================================
    // 一、存在性判断（由 ExistsByWrapper 注入, 所有表可用, WHERE 自动携带逻辑删除过滤）
    // ==================================================================================

    /**
     * 根据 Wrapper 条件,判断是否有数据存在
     * <p>详述: 由 {@link ExistsByWrapper} 注入, 生成 SQL: SELECT EXISTS (SELECT 1 FROM table WHERE ...)
     *
     * <p>亮点: EXISTS 子查询命中首条记录即短路返回, 相比 COUNT(*) 无需扫描全部匹配行;
     * <p>WHERE 自动携带逻辑删除(@TableLogic)过滤, 不会误判已删数据为存在
     *
     * @param queryWrapper 查询条件（不可为 null）
     * @return true存在,false不存在
     * @since 2026-08-08
     */
    Boolean existsByWrapper(@Param(Constants.WRAPPER) Wrapper<T> queryWrapper);


    /**
     * 根据 Consumer 包装的条件,判断是否有数据存在
     * <p>详述: 调用方自行决定条件, 使用 EXISTS 子查询判断, 复杂条件（多字段、模糊等）统一走本方法
     *
     * @param consumer LambdaQueryWrapper 消费函数,包装条件
     * @return true存在,false不存在
     * @since 2026-08-08
     */
    default Boolean existsByCondition(Consumer<LambdaQueryWrapper<T>> consumer) {
        LambdaQueryWrapper<T> lambdaQuery = Wrappers.lambdaQuery();
        consumer.accept(lambdaQuery);
        return existsByWrapper(lambdaQuery);
    }


    /**
     * 根据单个字段,判断是否有数据存在
     * <p>详述: 调用方指定比较的字段, 使用 EXISTS 子查询判断, 适合"用户名/邮箱是否已存在"等单字段唯一性校验
     * <p>注意: 此方法不支持复杂查询, 如: 多字段比较, 模糊查询等, 复杂条件请使用 {@link #existsByCondition(Consumer)}
     *
     * @param column 比较的字段
     * @param value  字段值
     * @return true存在,false不存在
     * @since 2026-08-08
     */
    default Boolean existsByColumn(SFunction<T, ?> column, Object value) {
        return existsByCondition(lambdaQuery -> lambdaQuery.eq(column, value));
    }


    // ==================================================================================
    // 二、全字段更新（由 MyBatis-Plus 官方 AlwaysUpdateSomeColumnById 注入, 所有表可用）
    // ==================================================================================

    /**
     * 根据主键更新全部字段（包括值为 null 的字段）
     * <p>详述: 由 MyBatis-Plus 官方 {@link AlwaysUpdateSomeColumnById} 注入,
     * 与 {@code updateById} 的区别是 SET 子句无条件包含除主键外的全部字段（不跳过 null）,
     * 适用于"把指定列置空"等 updateById 做不到的场景; WHERE 自动携带逻辑删除(@TableLogic)过滤
     * <p>注意: 传入实体除主键外的所有列都会原样写入数据库, 未赋值的列会被置 NULL!
     * 推荐用法: 先 selectById 读出完整实体, 修改后整体回写, 切勿用新建的空实体直接调用
     *
     * @param entity 实体（必须携带主键）
     * @return 更新行数
     * @since 2026-08-08
     */
    int alwaysUpdateSomeColumnById(@Param(Constants.ENTITY) T entity);


    // ==================================================================================
    // 三、逻辑删除（由 LogicDeleteById/LogicDeleteByIds 注入, 仅 @TableLogic 且实体含 deleteBy 字段的表可用）
    // ==================================================================================

    /**
     * 生成纳秒级 epoch 时间戳作为逻辑删除标识
     * <p>亮点: 比 MySQL UNIX_TIMESTAMP() 秒级精度高 10⁹ 倍, 高并发下基本不会重复;
     * 跨机器即使时钟微秒级漂移, 配合数据库唯一约束兜底, 冲突概率趋近于 0
     *
     * @return 纳秒级 epoch 时间戳
     * @since 2026-08-08
     */
    default long nanoEpoch() {
        Instant now = Instant.now();
        return now.getEpochSecond() * 1_000_000_000L + now.getNano();
    }


    /**
     * 根据主键逻辑删除（将逻辑删除列置为"已删除"值, 同时记录删除人）
     * <p>详述: 由 {@link LogicDeleteById} 注入, 生成 UPDATE 语句; WHERE 携带"未删除"条件,
     * 重复删除返回 0（天然幂等）
     * <p>生成的 SQL 形如: {@code UPDATE yeed_sys_user SET delete_time = ?, delete_by = ? WHERE id = ? AND delete_time = 0}
     * <p>注意: 本方法不做自动填充, 删除时间戳与删除人均由调用方传参,
     * 一般不直接调用, 统一走自动填充入口 {@link #deleteByIdAutoFill(Long)}
     *
     * @param id         主键
     * @param deleteTime 删除时间戳（纳秒级 epoch, 用 {@link #nanoEpoch()} 生成）
     * @param deleteBy   删除人（当前登录用户 ID）
     * @return 1-删除成功; 0-主键不存在或已被删除
     * @since 2026-08-08
     */
    int logicDeleteById(@Param("id") Long id,
                        @Param("deleteTime") long deleteTime,
                        @Param("deleteBy") Long deleteBy);


    /**
     * 根据主键逻辑删除（自动填充纳秒级删除时间戳与当前登录删除人）
     * <p>详述: {@link #logicDeleteById} 的自动填充入口, 删除时间戳由 {@link #nanoEpoch()} 生成,
     * 删除人取 Sa-Token 当前登录用户 ID
     * <p>亮点: 相比框架内置删除（deleteTime 由数据库 UNIX_TIMESTAMP() 秒级生成）,
     * Java 纳秒级时间戳高并发下不会同秒冲突导致唯一约束失败; 命名带 AutoFill 后缀,
     * 与官方 deleteById 相邻出现在代码补全中, 易于发现
     * <p>注意: 逻辑删除（标记删除）统一走本方法, 不要调用框架内置删除
     *
     * @param id 主键
     * @return true-删除成功; false-主键不存在或已被删除
     * @since 2026-08-08
     */
    default boolean deleteByIdAutoFill(Long id) {
        return logicDeleteById(id, nanoEpoch(), LoginUserHelper.requireUserId()) > 0;
    }


    /**
     * 根据主键集合批量逻辑删除（将逻辑删除列置为"已删除"值, 同时记录删除人）
     * <p>详述: 由 {@link LogicDeleteByIds} 注入, 生成带 IN 的 UPDATE 语句; WHERE 携带"未删除"条件,
     * 已删除或不存在的 id 自动跳过, 重复删除只作用于未删数据（天然幂等）
     * <p>生成的 SQL 形如: {@code UPDATE yeed_sys_user SET delete_time = ?, delete_by = ? WHERE id IN (?, ?) AND delete_time = 0}
     * <p>注意: 本方法不做自动填充, 删除时间戳与删除人均由调用方传参,
     * 一般不直接调用, 统一走自动填充入口 {@link #deleteByIdsAutoFill(Collection)};
     * 系统操作等无登录态场景由调用方显式传 deleteBy（如 0L 表示系统操作）;
     * ids 为空集合时 foreach 生成 IN () 导致 SQL 语法错误, 本方法不防御, 调用方必须保证非空
     *
     * @param ids        主键集合（不可为 null 或空集合）
     * @param deleteTime 删除时间戳（纳秒级 epoch, 用 {@link #nanoEpoch()} 生成）
     * @param deleteBy   删除人（当前登录用户 ID, 系统操作传 0L）
     * @return 实际删除行数, 可能小于 ids.size()（部分主键不存在或已被删除）
     * @since 2026-08-08
     */
    int logicDeleteByIds(@Param("ids") Collection<Long> ids,
                         @Param("deleteTime") long deleteTime,
                         @Param("deleteBy") Long deleteBy);


    /**
     * 根据主键集合批量逻辑删除（自动填充纳秒级删除时间戳与当前登录删除人）
     * <p>详述: {@link #logicDeleteByIds} 的自动填充入口, 空集合直接返回 0 不触库（对齐 MP 判空风格）;
     * 超过 {@link Constants#DEFAULT_BATCH_SIZE}（1000）条时自动分片执行, 避免 IN 列表过长,
     * 全部分片共享同一个删除时间戳与删除人（整批视为一次操作）
     * <p>亮点: 命名带 AutoFill 后缀, 与官方 deleteByIds 相邻出现在代码补全中, 易于发现
     * <p>注意: 逻辑删除（标记删除）统一走本方法, 不要调用框架内置删除;
     * 删除人取 Sa-Token 当前登录用户 ID, 未登录场景（定时任务等）将抛异常,
     * 系统操作请改调 {@link #logicDeleteByIds} 显式传 deleteBy;
     * 分片执行时各片为独立 UPDATE, 需要整体原子性请在 Service 层加事务
     *
     * @param ids 主键集合（null 或空集合返回 0）
     * @return 实际删除行数, 可能小于 ids.size()（部分主键不存在或已被删除）
     * @since 2026-08-08
     */
    default int deleteByIdsAutoFill(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        long deleteTime = nanoEpoch();
        Long deleteBy = LoginUserHelper.requireUserId();
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


    // ==================================================================================
    // 四、逻辑删除逃逸（忽略 @TableLogic 自动过滤, 仅 @TableLogic 表可用, 支撑"回收站"场景）
    // 开启全局逻辑删除后, MyBatis-Plus 所有内置方法都会自动追加"未删除"条件,
    // 导致"查不到已删数据、恢复不了已删数据、无法物理清除"三个死区, 本区方法即为解决这三个死区
    // ==================================================================================

    /**
     * 根据主键物理删除（真 DELETE, 不带 delete_time 未删除条件, 不可恢复）
     * <p>详述: 由 {@link PhysicalDeleteById} 注入; 逻辑删除表上 MyBatis-Plus 内置的 deleteById
     * 会被框架改写为 UPDATE, 本方法注入真正的 DELETE 语句, 用于"回收站彻底删除"等场景
     * <p>注意: 物理删除不可恢复, 业务侧应确保只对已逻辑删除的数据执行
     *
     * @param id 主键
     * @return 删除行数
     * @since 2026-08-08
     */
    int physicalDeleteById(Serializable id);


    /**
     * 根据 Wrapper 条件物理删除（真 DELETE, 不带 delete_time 未删除条件, 不可恢复）
     * <p>详述: 由 {@link PhysicalDelete} 注入, 用于按条件批量彻底清除
     * <p>亮点: Wrapper 未携带有效条件时兜底 WHERE 1=0, 一条都不删, 防止误删全表
     * <p>注意: 条件请全部通过 Wrapper 方法构建（Wrapper 上设置的 entity 条件不生效）;
     * 物理删除不可恢复, 业务侧应确保只对已逻辑删除的数据执行
     *
     * @param wrapper 删除条件
     * @return 删除行数
     * @since 2026-08-08
     */
    int physicalDelete(@Param(Constants.WRAPPER) Wrapper<T> wrapper);


    /**
     * 根据 Wrapper 条件查询列表（包含已逻辑删除的数据）
     * <p>详述: 由 {@link SelectListWithDeleted} 注入, 不带 delete_time 未删除条件;
     * Wrapper 为空条件时查全表（含已删）, 适合"回收站列表、已删数据审计"
     * <p>注意: 条件请全部通过 Wrapper 方法构建（Wrapper 上设置的 entity 条件不生效）
     *
     * @param wrapper 查询条件
     * @return 实体列表（含已删数据）
     * @since 2026-08-08
     */
    List<T> selectListWithDeleted(@Param(Constants.WRAPPER) Wrapper<T> wrapper);


    /**
     * 根据 Wrapper 条件分页查询（包含已逻辑删除的数据）
     * <p>详述: 由 {@link SelectPageWithDeleted} 注入, 不带 delete_time 未删除条件;
     * 方法签名携带 {@link IPage} 参数, 由 {@link PaginationInnerInterceptor} 自动完成分页,
     * 适合"回收站分页列表"场景; 业务侧一般不直接调用,
     * 统一走 {@link #selectPageVOWithDeleted(PageRequest, Wrapper, Function)} 便捷入口
     * <p>注意: 条件请全部通过 Wrapper 方法构建（Wrapper 上设置的 entity 条件不生效）
     *
     * @param page         分页参数（current/size）
     * @param queryWrapper 查询条件
     * @return 分页结果（含已删数据）
     * @since 2026-08-13
     */
    IPage<T> selectPageWithDeleted(IPage<T> page, @Param(Constants.WRAPPER) Wrapper<T> queryWrapper);


    /**
     * 根据主键恢复已逻辑删除的数据（逻辑删除列置回未删除值, 删除人一并置 NULL）
     * <p>详述: 由 {@link RestoreById} 注入, WHERE 携带"已删除"条件, 对未删除数据执行返回 0, 天然幂等;
     * 删除人一并置 NULL, 避免恢复后残留删除痕迹
     * <p>注意: 本方法不做自动填充, 如需记录恢复人/恢复时间, 由业务层在恢复后自行更新
     *
     * @param id 主键
     * @return 1-恢复成功; 0-主键不存在或数据未删除
     * @since 2026-08-08
     */
    int restoreById(Serializable id);


    // ==================================================================================
    // 五、分页查询（基于 MyBatis-Plus 内置 selectPage 封装, 所有表可用）
    //     PageRequest / PageResult 为 common-core 纯 POJO, 业务侧无需接触 ORM 分页类型
    // ==================================================================================

    /**
     * 分页查询并直接返回 VO 分页结果（一行完成"入参→IPage→查询→PageResult"三步）
     * <p>详述: 内部依次完成 PageRequest → MyBatis-Plus Page、selectPage 查询、Page → PageResult,
     * 页码/条数/总记录数的字段搬运全部收敛于此, 并始终基于 selectPage 返回结果组装,
     * 不会误用入参对象的旧数据; records 元素由 mapper 逐条转换
     * <p>注意: 元素转换（Entity→VO）通常传生成器产出的 XxxConvert::toVo;
     * 无需转换的场景请用 {@link #selectPageResult(PageRequest, Wrapper)}
     *
     * @param pageRequest  分页请求（pageNum / pageSize; 支持子类, 如 XxxPageDTO）
     * @param queryWrapper 查询条件（可空, 空则查全表）
     * @param mapper       Entity → VO 转换函数
     * @param <V>          VO 类型
     * @return 分页结果（records 已通过 mapper 转换为 VO）
     * @since 2026-08-12
     */
    default <V> PageResult<V> selectPageVO(PageRequest pageRequest, Wrapper<T> queryWrapper, Function<T, V> mapper) {
        Page<T> page = MybatisPageConverters.toMybatisPlusPage(pageRequest);
        return MybatisPageConverters.toPageResult(selectPage(page, queryWrapper), mapper);
    }


    /**
     * 分页查询并直接返回实体分页结果（无需元素转换时的便捷入口）
     * <p>详述: 等价于 {@code selectPageVO(pageRequest, queryWrapper, Function.identity())},
     * 元素不转换直接返回; 如需 Entity→VO 转换请用 {@link #selectPageVO(PageRequest, Wrapper, Function)}
     *
     * @param pageRequest  分页请求（pageNum / pageSize; 支持子类, 如 XxxPageDTO）
     * @param queryWrapper 查询条件（可空, 空则查全表）
     * @return 分页结果（records 为实体列表）
     * @since 2026-08-12
     */
    default PageResult<T> selectPageResult(PageRequest pageRequest, Wrapper<T> queryWrapper) {
        return selectPageVO(pageRequest, queryWrapper, Function.identity());
    }


    /**
     * 分页查询（包含已逻辑删除的数据）并直接返回 VO 分页结果
     * <p>详述: 与 {@link #selectPageVO(PageRequest, Wrapper, Function)} 行为一致, 唯一区别是不带
     * delete_time 未删除过滤, 用于"回收站分页列表"场景; 内部依次完成 PageRequest → IPage、
     * {@link #selectPageWithDeleted(IPage, Wrapper)} 查询、IPage → PageResult, 元素由 mapper 逐条转换
     * <p>注意: 仅 @TableLogic 表可用（否则对应方法未注入, 调用将抛 BindingException）
     *
     * @param pageRequest  分页请求（pageNum / pageSize; 支持子类, 如 XxxPageDTO）
     * @param queryWrapper 查询条件（可空, 空则查全表含已删）
     * @param mapper       Entity → VO 转换函数
     * @param <V>          VO 类型
     * @return 分页结果（records 已通过 mapper 转换为 VO, 含已删数据）
     * @since 2026-08-13
     */
    default <V> PageResult<V> selectPageVOWithDeleted(PageRequest pageRequest, Wrapper<T> queryWrapper, Function<T, V> mapper) {
        Page<T> page = MybatisPageConverters.toMybatisPlusPage(pageRequest);
        return MybatisPageConverters.toPageResult(selectPageWithDeleted(page, queryWrapper), mapper);
    }

}

