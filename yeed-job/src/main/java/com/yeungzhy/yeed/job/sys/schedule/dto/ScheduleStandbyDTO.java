package com.yeungzhy.yeed.job.sys.schedule.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 全局维护模式切换入参
 *
 * @author yeungzhy
 * @since 2026-09-08
 */
@Data
@Accessors(chain = true)
public class ScheduleStandbyDTO {

    /** true 进入维护模式（全部计划停止触发），false 恢复调度 */
    @NotNull(message = "维护模式开关不能为空")
    private Boolean standby;

}
