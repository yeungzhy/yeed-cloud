package com.yeungzhy.yeed.job.sys.export.task.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yeungzhy.yeed.common.data.mybatis.BaseMapper;
import com.yeungzhy.yeed.job.sys.export.task.entity.ExportTask;
import com.yeungzhy.yeed.job.sys.export.task.enums.ExportTaskStatusEnum;
import org.apache.ibatis.annotations.Mapper;

/**
 * 导出任务表 Mapper 接口
 *
 * @author yeungzhy
 * @since 2026-09-01 21:55:57
 */
@Mapper
public interface ExportTaskMapper extends BaseMapper<ExportTask> {


    /**
     * 回写导出成功：状态置 SUCCESS，并记录成品文件ID
     *
     * @param exportTaskId 导出任务主键
     * @param ossId        成品文件记录主键
     * @param size         成品文件字节大小
     */
    default void updateSuccess(Long exportTaskId, Long ossId, int size) {
        update(null, Wrappers.<ExportTask>lambdaUpdate()
                .set(ExportTask::getStatus, ExportTaskStatusEnum.SUCCESS.getCode())
                .set(ExportTask::getOssId, ossId)
                .set(ExportTask::getFileSize, size)
                .eq(ExportTask::getId, exportTaskId));
    }


    /**
     * 回写导出失败：状态置 FAILED，并记录失败原因
     *
     * @param exportTaskId 导出任务主键
     * @param errorMsg     失败原因
     */
    default void updateFailed(Long exportTaskId, String errorMsg) {
        update(null, Wrappers.<ExportTask>lambdaUpdate()
                .set(ExportTask::getStatus, ExportTaskStatusEnum.FAILED.getCode())
                .set(ExportTask::getFailReason, errorMsg)
                .eq(ExportTask::getId, exportTaskId));
    }

    /**
     * 更新导出进度（百分比）
     *
     * @param exportTaskId 导出任务主键
     * @param progress     进度值（0~100）
     */
    default void updateProgress(Long exportTaskId, int progress) {
        update(null, Wrappers.<ExportTask>lambdaUpdate()
                .set(ExportTask::getProgress, progress)
                .eq(ExportTask::getId, exportTaskId));
    }

}
