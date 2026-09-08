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
 * 数据实体基类，业务实体统一继承本类。
 *
 * <p>字段分四组：
 * <ul>
 *   <li>主键：雪花 ID（由 {@link CustomIdGenerator} 生成）</li>
 *   <li>审计字段：创建/更新人、时间（由 {@link AutoFillFieldHandler} 自动填充）</li>
 *   <li>逻辑删除：deleteTime 纳秒时间戳（区别于常规 1/0 标记，避免高并发同秒删除唯一冲突）</li>
 *   <li>架构字段：乐观锁 version、JSON 扩展字段 extra</li>
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

    /** 雪花ID主键 */
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

    /** 删除人 */
    private Long deleteBy;
    /**
     * 逻辑删除标识：0-未删，纳秒级时间戳-已删。
     * <p>用纳秒时间戳作删除标记（而非 1/0），避免高并发下同一秒删除造成唯一约束冲突；
     * 删除时由 {@link BaseMapper#deleteByIdAutoFill(Long)} 填充，精度高于 UNIX_TIMESTAMP() 的秒级。
     * <p>全局逻辑删除配置见 datasource.yaml：
     * <pre>
     * mybatis-plus.global-config.db-config:
     *   logic-delete-field: deleteTime
     *   logic-delete-value: 'UNIX_TIMESTAMP()'
     *   logic-not-delete-value: 0
     * </pre>
     */
    private Long deleteTime;

    // ================== 架构字段 ==================
    /** 乐观锁 */
    @Version
    private Integer version;

    // ================== 扩展字段 ==================
    /**
     * 扩展字段（DB 为 json 列）
     * <p>使用 JacksonTypeHandler 在 Map&lt;String,Object&gt; 与 JSON 之间互转；
     * <p>实体需配合 @TableName(autoResultMap = true) 才能在查询结果映射时生效
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extra;



    /**
     * 通过 Lambda 方法引用获取数据库列名（驼峰转下划线）。
     * <p>适用于：静态常量定义、SQL apply、XML 拼接等需要字符串列名的场景；
     * 例：{@code getCol(SysUser::getCreateTime)} 返回 {@code create_time}。
     */
    public static <T> String getCol(SFunction<T, ?> fn) {
        // Lambda 方法引用 -> 方法名(getCreateTime) -> 属性名(createTime)
        String property = PropertyNamer.methodToProperty(LambdaUtils.extract(fn).getImplMethodName());
        // 属性名驼峰转下划线 -> create_time
        return StringUtils.camelToUnderline(property);
    }


}
