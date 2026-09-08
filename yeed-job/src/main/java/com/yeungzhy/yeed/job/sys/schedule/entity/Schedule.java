package com.yeungzhy.yeed.job.sys.schedule.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import com.yeungzhy.yeed.common.data.model.BaseEntity;
import com.yeungzhy.yeed.job.sys.schedule.enums.ScheduleConcurrentEnum;
import com.yeungzhy.yeed.job.sys.schedule.enums.ScheduleMisfireEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

import java.time.LocalTime;

/**
 * 定时任务定义表
 *
 * @author yeungzhy
 * @since 2026-09-02 23:52:19
 */
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName(value = "yeed_sys_schedule", autoResultMap = true)
public class Schedule extends BaseEntity {

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
    /** 并发策略:0-跳过(上次未结束则放弃本次),1-允许,2-排队(结束后补跑) */
    private ScheduleConcurrentEnum allowConcurrent;
    /**
     * 每日可执行时段起始
     * <p>与 {@link #endTime} 同时为 null 表示不限制时段，两列不能只填一个。
     * 结束时刻早于起始时刻视为跨零点，如 22:00 到次日 06:00
     */
    private LocalTime startTime;
    /** 每日可执行时段结束，为 null 时表示不限制时段 */
    private LocalTime endTime;
    /** 状态:0-停用,1-启用 */
    private EnableStatusEnum status;
    /** 备注 */
    private String remark;

}
