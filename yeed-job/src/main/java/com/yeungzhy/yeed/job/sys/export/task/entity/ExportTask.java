package com.yeungzhy.yeed.job.sys.export.task.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yeungzhy.yeed.common.data.model.BaseEntity;
import com.yeungzhy.yeed.job.sys.export.task.enums.ExportTaskStatusEnum;
import lombok.Data;
import org.springframework.util.StringUtils;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 导出任务表
 *
 * @author yeungzhy
 * @since 2026-09-01 21:55:57
 */
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName(value = "yeed_sys_export_task", autoResultMap = true)
public class ExportTask extends BaseEntity {

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

    /**
     * 水印文本：姓名 + 工号，供导出文件的防泄密追溯使用
     *
     * <p> 两个快照都为空时返回 null，创建人未知（如系统触发的导出）时不画水印，
     * 而不是铺一层空白底纹。取名不带 get 前缀：它是派生值，不是持久列，
     * 避免 MyBatis-Plus 与 MapStruct 把它当成字段处理
     *
     * @return 水印文本；创建人快照为空时返回 null
     */
    public String waterMarkText() {
        if (!StringUtils.hasText(createByRealName) && !StringUtils.hasText(createByEmployeeNo)) {
            return null;
        }
        return StringUtils.hasText(createByEmployeeNo) ? createByRealName + " " + createByEmployeeNo : createByRealName;
    }

}
