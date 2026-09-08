package com.yeungzhy.yeed.api.export.task.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 导出任务分页查询 VO
 *
 * @author yeungzhy
 * @since 2026-09-01 21:55:57
 */
@Data
@Accessors(chain = true)
public class ExportTaskPageVO {

    // ================== 主键 ==================
    /** 雪花 ID 主键 */
    private Long id;

    // ================== 业务字段 ==================
    /** 任务状态：0-待执行，1-执行中，2-成功，3-失败 */
    private Integer status;
    /** 进度百分比，0-100：写文件阶段在 10~90 之间推进，上传停在 90，成功置 100 */
    private Integer progress;
    /** 开始执行时间 */
    private LocalDateTime startTime;
    /** 结束时间，成功与失败均写入（终态必带） */
    private LocalDateTime finishTime;
    /** 失败原因，仅失败任务非空 */
    private String failReason;
    /** 已重试次数 */
    private Integer retryCount;
    /** 导出文件名（含扩展名），创建时由业务侧生成 */
    private String fileName;
    /** OSS 文件记录主键，仅成功任务非空，前端据此调下载接口 */
    private Long ossId;
    /** 文件大小（字节），导出成功后写入 */
    private Long fileSize;

    // ================== 审计字段 ==================
    /** 创建人 */
    private Long createBy;
    /** 创建时间 */
    private LocalDateTime createTime;

}
