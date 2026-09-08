package com.yeungzhy.yeed.admin.sys.role.dto;

import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import com.yeungzhy.yeed.common.core.request.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 系统角色 分页查询 DTO（前端入参）
 *
 * <p>三个筛选字段都为 null 时表示不过滤；排序字段由 Service 按白名单应用，不在本类中约束
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:14
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class SysRolePageDTO extends PageRequest {

    /** 角色名称（模糊匹配） */
    private String roleName;
    /** 角色编码（模糊匹配） */
    private String roleCode;
    /** 状态（精确匹配） */
    private EnableStatusEnum status;

}
