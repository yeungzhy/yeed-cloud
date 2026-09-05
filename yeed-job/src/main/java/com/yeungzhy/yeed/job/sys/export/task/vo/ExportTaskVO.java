package com.yeungzhy.yeed.job.sys.export.task.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yeungzhy.yeed.job.sys.export.task.enums.ExportTaskStatusEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 导出任务表 VO（返回出参）
 *
 * @author yeungzhy
 * @since 2026-09-01 21:55:57
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExportTaskVO {

    // ================== 主键 ==================
    /** 雪花ID主键 */
    private Long id;

    // ================== 业务字段 ==================
    /** 导出业务类型 */
    private String exportType;
    /** 导出数据查询参数 */
    private String queryParam;
    /** 任务状态:0-待执行,1-执行中,2-成功,3-失败 */
    private ExportTaskStatusEnum status;
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
    /** 创建人姓名快照 */
    private String createByRealName;
    /** 创建人工号快照 */
    private String createByEmployeeNo;

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
