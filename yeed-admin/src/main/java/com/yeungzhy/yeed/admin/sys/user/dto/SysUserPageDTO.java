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
 * <p>手机 / 邮箱是加密列，等值查询走盲索引，不能用 like；查询条件的装配见 Service 层
 *
 * @author yeungzhy
 * @since 2026-08-13 06:48:01
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class SysUserPageDTO extends PageRequest {

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


    /**
     * 是否存在创建时间范围
     *
     * <p>start / end 必须成对，缺一则视为不按时间过滤；单独判一端会把边界外的数据误过滤掉
     *
     * @return 两端都非空时返回 true
     */
    public boolean hasCreateTimeRange() {
        return createTimeStart != null && createTimeEnd != null;
    }

}
