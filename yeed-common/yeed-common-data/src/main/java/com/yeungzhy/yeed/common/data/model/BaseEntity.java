package com.yeungzhy.yeed.common.data.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.yeungzhy.yeed.common.data.mybatis.AutoFillFieldHandler;
import com.yeungzhy.yeed.common.data.mybatis.BaseMapper;
import com.yeungzhy.yeed.common.data.mybatis.CustomIdGenerator;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.apache.ibatis.reflection.property.PropertyNamer;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 数据实体基类，业务实体统一继承本类
 *
 * <p> 字段分四组：
 * <ul>
 *   <li>主键：雪花 ID（由 {@link CustomIdGenerator} 生成）
 *   <li>审计字段：创建/更新人、时间（由 {@link AutoFillFieldHandler} 自动填充）
 *   <li>逻辑删除：deleteTime 纳秒时间戳（区别于常规 1/0 标记，避免高并发同秒删除唯一冲突）
 *   <li>架构字段：乐观锁 version、JSON 扩展字段 extra
 * </ul>
 *
 * <p> 新建业务表的 DDL 模板见 docs/base-table.sql，公共字段与本类一一对应，改一处必须同步另一处
 *
 * @author yeungzhy
 * @since 2026-07-30
 */
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants
@Accessors(chain = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public abstract class BaseEntity {

    /** 雪花 ID 主键，由 {@link CustomIdGenerator} 生成 */
    @EqualsAndHashCode.Include
    private Long id;

    // ================== 审计字段 ==================

    /** 创建人 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新人 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 删除人，逻辑删除时与 deleteTime 一同写入
     *
     * <p> 无登录态的删除场景（定时任务、系统清理）传 {@code 0L}：本列不参与逻辑删除判定，留空不影响查询
     */
    private Long deleteBy;

    /**
     * 逻辑删除标识：0 未删，纳秒级时间戳已删
     *
     * <p> 用纳秒时间戳而不是 1/0 作删除标记：删除后原唯一索引仍生效，1/0 会让同一条业务键的多次删除
     * 撞唯一约束，时间戳则每次都不同
     *
     * <p> 时间戳由 {@link BaseMapper#deleteByIdAutoFill(Long)} 在 Java 侧生成，不交给数据库函数：
     * {@code UNIX_TIMESTAMP()} 只到秒，高并发下同秒删除照样撞约束
     *
     * <p> 全局配置在 datasource.yaml：
     * <pre>{@code
     * mybatis-plus.global-config.db-config:
     *   logic-delete-field: deleteTime
     *   logic-delete-value: 'UNIX_TIMESTAMP()'
     *   logic-not-delete-value: 0
     * }</pre>
     */
    private Long deleteTime;

    // ================== 架构字段 ==================

    /**
     * 乐观锁版本号，仅 {@code updateById} 等按主键更新时生效，批量 / Wrapper 更新不触发
     *
     * <p> 只想更新个别列时用 {@code update(null, wrapper)}，不带本字段就不会启乐观锁
     */
    @Version
    private Integer version;

    // ================== 扩展字段 ==================

    /**
     * 扩展字段（DB 为 json 列）
     *
     * <p> 由 JacksonTypeHandler 在 {@code Map<String, Object>} 与 JSON 之间互转；
     * 实体必须配合 {@code @TableName(autoResultMap = true)}，否则查询结果映射时不走该 TypeHandler，
     * 取出来会是原始 JSON 字符串
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extra;

    /**
     * 通过 Lambda 方法引用取数据库列名（驼峰转下划线）
     *
     * <p> 用于 SQL 拼接、XML、常量定义这类只能拿字符串列名的场景；
     * 能写 Lambda 的地方直接用 {@code SFunction}，不必绕一层字符串
     *
     * @param fn 实体属性的方法引用，如 {@code SysUser::getCreateTime}，不能为 null
     * @param <T> 实体类型
     * @return 下划线列名，如 {@code create_time}
     */
    public static <T> String getCol(SFunction<T, ?> fn) {
        // 方法引用 -> 方法名(getCreateTime) -> 属性名(createTime)
        String property = PropertyNamer.methodToProperty(LambdaUtils.extract(fn).getImplMethodName());
        // 属性名驼峰转下划线 -> create_time
        return StringUtils.camelToUnderline(property);
    }

}
