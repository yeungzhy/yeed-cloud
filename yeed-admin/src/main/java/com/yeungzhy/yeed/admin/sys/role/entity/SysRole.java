package com.yeungzhy.yeed.admin.sys.role.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yeungzhy.yeed.common.data.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

/**
 * 系统角色
 *
 * @author yeungzhy
 * @since 2026-08-09 10:25:37
 */
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName(value = "yeed_sys_role", autoResultMap = true)
public class SysRole extends BaseEntity {

    /** 角色名称 */
    private String roleName;
    /** 角色编码（业务唯一标识） */
    private String roleCode;
    /** 角色描述 */
    private String description;
    /** 状态：0-禁用，1-启用 */
    private Integer status;

}
