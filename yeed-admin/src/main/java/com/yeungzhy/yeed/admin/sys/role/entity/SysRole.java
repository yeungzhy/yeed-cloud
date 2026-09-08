package com.yeungzhy.yeed.admin.sys.role.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yeungzhy.yeed.common.core.enums.BuiltinRoleEnum;
import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import com.yeungzhy.yeed.common.data.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

import java.beans.Transient;

/**
 * 系统角色
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:14
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
    private EnableStatusEnum status;


    /**
     * 是否为内置角色（roleCode 命中内置白名单）
     *
     * <p>用 JDK 标准 {@link Transient} 声明非持久化，避免 POJO 依赖具体 ORM / JSON 框架的忽略注解
     */
    @Transient
    public boolean isBuiltin() {
        return BuiltinRoleEnum.fromCodeOrNull(this.roleCode) != null;
    }

}
