package com.yeungzhy.yeed.admin.sys.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yeungzhy.yeed.common.data.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

/**
 * 用户角色关联表
 *
 * @author yeungzhy
 * @since 2026-08-13 06:56:05
 */
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName(value = "yeed_sys_user_role", autoResultMap = true)
public class SysUserRole extends BaseEntity {

    /** 用户ID */
    private Long userId;
    /** 角色ID */
    private Long roleId;

}
