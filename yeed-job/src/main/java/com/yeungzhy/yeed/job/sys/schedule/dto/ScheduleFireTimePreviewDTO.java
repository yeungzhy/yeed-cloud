package com.yeungzhy.yeed.job.sys.schedule.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * CRON 表达式的下次执行时间预览入参
 *
 * <p> 解析交给后端而非前端：Quartz 的 CRON 是 6/7 位且带 {@code L} / {@code W} / {@code #} 等扩展字符，
 * 通用 CRON 库算出来的结果与 Quartz 实际行为不一致，用前端库预览会给出错误承诺
 *
 * @author yeungzhy
 * @since 2026-09-08
 */
@Data
@Accessors(chain = true)
public class ScheduleFireTimePreviewDTO {

    /** 待预览的 CRON 表达式 */
    @NotBlank(message = "CRON 表达式不能为空")
    private String cronExpr;

    /** 预览条数 */
    @Min(value = 1, message = "预览条数至少为 1")
    @Max(value = 20, message = "预览条数最多为 20")
    private Integer count = 5;

}
