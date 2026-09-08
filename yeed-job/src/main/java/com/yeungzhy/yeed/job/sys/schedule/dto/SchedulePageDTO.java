package com.yeungzhy.yeed.job.sys.schedule.dto;

import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import com.yeungzhy.yeed.common.core.request.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 定时任务定义表 分页查询 DTO（前端入参）
 *
 * @author yeungzhy
 * @since 2026-09-02 23:52:19
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class SchedulePageDTO extends PageRequest {

    /** 业务分组编码，精确匹配 */
    private String groupCode;
    /** 任务名称，模糊匹配 */
    private String name;
    /** 状态，精确匹配 */
    private EnableStatusEnum status;

}
