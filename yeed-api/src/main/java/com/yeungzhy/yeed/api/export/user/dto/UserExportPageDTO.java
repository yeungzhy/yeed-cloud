package com.yeungzhy.yeed.api.export.user.dto;

import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import com.yeungzhy.yeed.common.core.request.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 用户导出 分页查询 DTO
 *
 * @author yeungzhy
 * @since 2026-08-13 06:48:01
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class UserExportPageDTO extends PageRequest {

    /** 系统登录名 */
    private String username;
    /** 手机号 */
    private String phone;
    /** 邮箱 */
    private String email;
    /** 状态：0-禁用，1-启用 */
    private EnableStatusEnum status;

    /** 创建时间 - 起始（含边界） */
    private LocalDateTime createTimeStart;
    /** 创建时间 - 结束（含边界） */
    private LocalDateTime createTimeEnd;

}
