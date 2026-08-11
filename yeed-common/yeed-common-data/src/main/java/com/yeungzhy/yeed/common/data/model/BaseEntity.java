package com.yeungzhy.yeed.common.data.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.yeungzhy.yeed.common.data.mybatis.BaseMapper;
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
 * 项目基类, 包括审计字段\架构字段
 *
 * @author yeungzhy at 2026-07-30 22:38
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
     * 逻辑删除标识：0-未删，纳秒级时间戳-已删
     * <p> 避免高并发下同一秒删除导致唯一约束冲突
     * <p> 由 {@link BaseMapper#deleteByIdAutoFill(Long)} 填充纳秒级时间戳，比 UNIX_TIMESTAMP() 秒级精度高
     *
     * <p> 已在配置中开启全局逻辑删除属性，保留 'UNIX_TIMESTAMP()' 兜底
     * {@snippet lang="yaml":
     * mybatis-plus:
     *   global-config:
     *     db-config:
     *       logic-delete-field: deleteTime
     *       logic-delete-value: 'UNIX_TIMESTAMP()'
     *       logic-not-delete-value: 0
     * }
     */
    private Long deleteTime;

    // ================== 架构字段 ==================
    /** 乐观锁 */
    @Version
    private Integer version;

    // ================== 扩展字段 ==================
    /**
     * 扩展字段（DB 为 json 列）
     * <p> 使用 JacksonTypeHandler 在 Map&lt;String,Object&gt; 与 JSON 之间互转；
     * <p> 实体需配合 @TableName(autoResultMap = true) 才能在查询结果映射时生效
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extra;



    /**
     * 通过 Lambda 方法引用获取数据库列名
     * <p> 适用于：静态常量定义、SQL apply、XML 拼接等需要字符串列名的场景
     */
    public static <T> String getCol(SFunction<T, ?> fn) {
        // 解析 Lambda 提取方法名 -> getCreateTime -> createTime
        String property = PropertyNamer.methodToProperty(LambdaUtils.extract(fn).getImplMethodName());
        // 驼峰转下划线 -> createTime -> create_time
        return StringUtils.camelToUnderline(property);
    }


}
