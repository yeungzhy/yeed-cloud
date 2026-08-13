package com.yeungzhy.yeed.admin.sys.role.vo;

import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 系统角色 分页列表出参 VO
 *
 * <p>仅承载分页列表展示所需业务字段；审计字段（创建/更新人、时间等）属
 * 详情编辑回显场景（见 {@link SysRoleVO}），不在列表接口暴露。
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:14
 */
@Data
@Accessors(chain = true)
public class SysRolePageVO {

    /** 雪花ID主键 */
    private Long id;
    /** 角色名称 */
    private String roleName;
    /** 角色编码（业务唯一标识） */
    private String roleCode;
    /** 角色描述 */
    private String description;
    /** 状态：0-禁用，1-启用 */
    private EnableStatusEnum status;

}
