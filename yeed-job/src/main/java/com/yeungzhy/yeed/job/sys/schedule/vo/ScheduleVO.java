package com.yeungzhy.yeed.job.sys.schedule.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import com.yeungzhy.yeed.job.sys.schedule.enums.ScheduleConcurrentEnum;
import com.yeungzhy.yeed.job.sys.schedule.enums.ScheduleMisfireEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * 定时任务定义表 VO（返回出参）
 *
 * @author yeungzhy
 * @since 2026-09-02 23:52:19
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScheduleVO {

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
    /** 备注 */
    private String remark;

    // ================== 运行时字段（取自 Quartz，非持久列） ==================
    /** 上次触发时间，从未触发过为 null */
    private LocalDateTime previousFireTime;
    /** 下次触发时间，已停用或已过结束时间为 null */
    private LocalDateTime nextFireTime;

    // ================== 审计字段 ==================
    /** 创建人 */
    private Long createBy;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新人 */
    private Long updateBy;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 删除人 */
    private Long deleteBy;
    /** 逻辑删除,0-未删,时间戳-已删 */
    private Long deleteTime;
    /** 乐观锁 */
    private Integer version;
    /** 扩展信息 */
    private Map<String, Object> extra;

}
