package com.yeungzhy.yeed.admin.sys.role.dto;

import com.yeungzhy.yeed.common.core.request.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 系统角色 分页查询 DTO（前端入参）
 *
 * @author yeungzhy
 * @since 2026-08-09 16:03:19
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class SysRolePageDTO extends PageRequest {


}
