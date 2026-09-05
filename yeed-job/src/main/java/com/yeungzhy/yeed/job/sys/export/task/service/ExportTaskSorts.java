package com.yeungzhy.yeed.job.sys.export.task.service;

import com.yeungzhy.yeed.job.sys.export.task.entity.ExportTask;
import com.yeungzhy.yeed.common.data.model.BaseEntity;
import com.yeungzhy.yeed.common.data.request.BaseSorts;
import org.springframework.stereotype.Component;

/**
 * 导出任务表 排序字段白名单
 *
 * @author yeungzhy
 * @since 2026-09-01 21:55:57
 */
@Component
public class ExportTaskSorts extends BaseSorts<ExportTask> {

    protected ExportTaskSorts() {
        super(ExportTask.class, new BaseSorts.Builder<ExportTask>()
                .add(BaseEntity.Fields.createTime, ExportTask::getCreateTime)
                // 多个字段排序时,要加上主键作为决胜字段,防止在其他字段相同的发生数据错乱或漏页
                .add(BaseEntity.Fields.id, ExportTask::getId)
        );
    }

}
