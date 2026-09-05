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
    /** 雪花ID主键 */
    private Long id;

    // ================== 业务字段 ==================
    /** 任务状态:0-待执行,1-执行中,2-成功,3-失败 */
    private Integer status;
    /** 进度百分比0-100 */
    private Integer progress;
    /** 开始执行时间 */
    private LocalDateTime startTime;
    /** 结束时间(成功/失败均记录) */
    private LocalDateTime finishTime;
    /** 失败原因 */
    private String failReason;
    /** 已重试次数 */
    private Integer retryCount;
    /** 导出文件名(含扩展名) */
    private String fileName;
    /** OSS文件记录主键ID */
    private Long ossId;
    /** 文件大小(字节,导出成功后写入) */
    private Long fileSize;

    // ================== 审计字段 ==================
    /** 创建人 */
    private Long createBy;
    /** 创建时间 */
    private LocalDateTime createTime;

}
