package com.yeungzhy.yeed.admin.sys.user.dto;

import com.yeungzhy.yeed.common.core.request.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 系统用户 分页查询 DTO（前端入参）
 *
 * @author yeungzhy
 * @since 2026-08-13 06:48:01
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class SysUserPageDTO extends PageRequest {


}
