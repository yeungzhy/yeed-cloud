package com.yeungzhy.yeed.common.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 项目基类, 包括审计字段\架构字段
 *
 * @author yeungzhy at 2026-07-30 22:38
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class BaseEntity {

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

    // TODO 删除操作人 (未删除时为null) 如何定义填充策略，仅在逻辑删除时填充, 似乎是得包一层BaseService了
    /** 删除人 */
    @TableField(fill = FieldFill.UPDATE)
    private Long deleteBy;
    /** 逻辑删除,0-未删,时间戳-已删 */
    private Long deleteTime;

    // ================== 架构字段 ==================
    /** 乐观锁 */
    @Version
    private Integer version;

    // ================== 扩展字段 ==================
    /** 扩展字段 */
    private Map<String, Object> extra;

}
