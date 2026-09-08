package com.yeungzhy.yeed.job.sys.schedule.dto;

import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import com.yeungzhy.yeed.job.sys.schedule.enums.ScheduleConcurrentEnum;
import com.yeungzhy.yeed.job.sys.schedule.enums.ScheduleMisfireEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalTime;

/**
 * 定时任务定义表 DTO（前端入参）
 *
 * @author yeungzhy
 * @since 2026-09-02 23:52:19
 */
@Data
@Accessors(chain = true)
public class ScheduleDTO {

    // ================== 主键 ==================
    /** 雪花ID主键 */
    private Long id;

    // ================== 业务字段 ==================
    /** 业务分组编码 */
    private String groupCode;
    /** 任务名称(组内唯一) */
    private String name;
    /** CRON表达式 */
    private String cronExpr;
    /** 错过触发补偿策略:0-丢弃,1-补跑一次 */
    private ScheduleMisfireEnum misfireInstr;
    /** Spring Bean名称 */
    private String beanName;
    /** Bean方法名 */
    private String methodName;
    /** 并发策略:0-跳过,1-允许,2-排队 */
    private ScheduleConcurrentEnum allowConcurrent;
    /** 每日可执行时段起始，与结束时间同时为空表示不限制 */
    private LocalTime startTime;
    /** 每日可执行时段结束，与起始时间同时为空表示不限制 */
    private LocalTime endTime;
    /** 状态:0-停用,1-启用 */
    private EnableStatusEnum status;


}
