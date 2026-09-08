package com.yeungzhy.yeed.job.sys.schedule.service;

import com.yeungzhy.yeed.common.data.model.BaseEntity;
import com.yeungzhy.yeed.common.data.request.BaseSorts;
import com.yeungzhy.yeed.job.sys.schedule.entity.Schedule;
import org.springframework.stereotype.Component;

/**
 * 定时任务定义表 排序字段白名单
 *
 * @author yeungzhy
 * @since 2026-09-02 23:52:19
 */
@Component
public class ScheduleSorts extends BaseSorts<Schedule> {

    protected ScheduleSorts() {
        super(Schedule.class, new BaseSorts.Builder<Schedule>()
                .add(BaseEntity.Fields.createTime, Schedule::getCreateTime)
                .add(Schedule.Fields.groupCode, Schedule::getGroupCode)
                .add(Schedule.Fields.name, Schedule::getName)
                .add(Schedule.Fields.status, Schedule::getStatus)
                // 多个字段排序时,要加上主键作为决胜字段,防止在其他字段相同的发生数据错乱或漏页
                .add(BaseEntity.Fields.id, Schedule::getId)
        );
    }

}
