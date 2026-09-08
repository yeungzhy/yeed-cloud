package com.yeungzhy.yeed.job.sys.schedule.quartz;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yeungzhy.yeed.job.sys.schedule.entity.Schedule;
import com.yeungzhy.yeed.job.sys.schedule.mapper.ScheduleMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动装载器：把库中的计划全量同步到 Quartz
 *
 * <p>数据库是调度的唯一真源，实例重启后无需人工干预即可恢复全部计划。
 * 两个实例同时装载是幂等的，Quartz 集群共用一套 QRTZ_* 表，触发器只会被抢到一次
 *
 * @author yeungzhy
 * @since 2026-09-08
 */
@Slf4j
@Component
public class ScheduleLoader implements ApplicationRunner {

    @Resource
    private ScheduleMapper scheduleMapper;
    @Resource
    private ScheduleRegistry scheduleRegistry;

    /**
     * 装载全部计划，停用的计划注册后由注册表暂停
     *
     * @param args 启动参数，不使用
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("启动装载定时计划完成, 共 {} 条", reloadAll());
    }

    /**
     * 按当前库数据重新装载全部计划，等价于重启一次装载
     *
     * <p>用于生产环境无法重启时修复"库改了、Quartz 没同步"，同时覆盖改 CRON、改并发策略、
     * 改时段窗口等一切以库为准的变更。本方法只做新增与覆盖，不清理 Quartz 里多余的定义
     *
     * @return 装载条数
     */
    public int reloadAll() {
        // selectList 自带逻辑删除条件
        List<Schedule> schedules = scheduleMapper.selectList(Wrappers.lambdaQuery());
        schedules.forEach(scheduleRegistry::upsert);
        return schedules.size();
    }

    /**
     * 重新装载单条计划
     *
     * @param schedule 计划定义
     */
    public void reloadOne(Schedule schedule) {
        scheduleRegistry.upsert(schedule);
    }

}
