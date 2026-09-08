package com.yeungzhy.yeed.job.sys.schedule.mapper;

import com.yeungzhy.yeed.common.data.mybatis.BaseMapper;
import com.yeungzhy.yeed.job.sys.schedule.entity.Schedule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务定义表 Mapper 接口
 *
 * @author yeungzhy
 * @since 2026-09-02 23:52:19
 */
@Mapper
public interface ScheduleMapper extends BaseMapper<Schedule> {


}
