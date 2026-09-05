package com.yeungzhy.yeed.job.sys.export.task.mapper;

import com.yeungzhy.yeed.common.data.mybatis.BaseMapper;
import com.yeungzhy.yeed.job.sys.export.task.entity.ExportTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 导出任务表 Mapper 接口
 *
 * @author yeungzhy
 * @since 2026-09-01 21:55:57
 */
@Mapper
public interface ExportTaskMapper extends BaseMapper<ExportTask> {


    default void updateSuccess(Long exportTaskId, Long ossId, int size) {

    }


    default void updateFailed(Long exportTaskId, String errorMsg) {

    }

    default void updateProgress(Long exportTaskId, int progress) {

    }

}
