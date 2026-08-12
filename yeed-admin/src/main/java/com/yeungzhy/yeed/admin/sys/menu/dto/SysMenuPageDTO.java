package com.yeungzhy.yeed.admin.sys.menu.dto;

import com.yeungzhy.yeed.common.core.request.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 系统菜单权限表 分页查询 DTO（前端入参）
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:30
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class SysMenuPageDTO extends PageRequest {


}
