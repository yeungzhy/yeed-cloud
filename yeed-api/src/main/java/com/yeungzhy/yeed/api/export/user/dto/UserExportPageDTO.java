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
 * <p>字段与 admin 侧列表查询条件一一对应（由 {@code SysUserConvert} 转换），任一侧加筛选字段都要同步另一处，
 * 否则导出结果与列表所见不一致
 *
 * <p>一次调用同时承载筛选与分页：{@code exportTotal} 只取筛选部分，{@code exportPage} 两者都用
 *
 * @author yeungzhy
 * @since 2026-08-13 06:48:01
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class UserExportPageDTO extends PageRequest {

    /** 系统登录名（模糊） */
    private String username;
    /** 真实姓名（模糊） */
    private String realName;
    /** 工号（模糊） */
    private String employeeNo;
    /** 手机号（精确，走盲索引） */
    private String phone;
    /** 邮箱（精确，走盲索引） */
    private String email;
    /** 状态：0-禁用，1-启用 */
    private EnableStatusEnum status;

    /** 创建时间 - 起始（含边界） */
    private LocalDateTime createTimeStart;
    /** 创建时间 - 结束（含边界） */
    private LocalDateTime createTimeEnd;

}
