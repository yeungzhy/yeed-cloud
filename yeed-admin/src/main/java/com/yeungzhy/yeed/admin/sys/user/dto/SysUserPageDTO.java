package com.yeungzhy.yeed.admin.sys.user.dto;

import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import com.yeungzhy.yeed.common.core.request.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

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

    /** 用户名 */
    private String username;
    /** 邮箱 */
    private String email;
    /** 状态：0-禁用，1-启用 */
    private EnableStatusEnum status;

    /** 创建时间 */
    private LocalDateTime createTime;

}
