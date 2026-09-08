package com.yeungzhy.yeed.job.sys.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import com.yeungzhy.yeed.common.core.exception.BizAssert;
import com.yeungzhy.yeed.common.core.exception.BizException;
import com.yeungzhy.yeed.common.core.result.PageResult;
import com.yeungzhy.yeed.job.sys.schedule.dto.ScheduleDTO;
import com.yeungzhy.yeed.job.sys.schedule.dto.ScheduleFireTimePreviewDTO;
import com.yeungzhy.yeed.job.sys.schedule.dto.SchedulePageDTO;
import com.yeungzhy.yeed.job.sys.schedule.entity.Schedule;
import com.yeungzhy.yeed.job.sys.schedule.mapper.ScheduleMapper;
import com.yeungzhy.yeed.job.sys.schedule.quartz.ScheduleRegistry;
import com.yeungzhy.yeed.job.sys.schedule.quartz.ScheduleTargetValidator;
import com.yeungzhy.yeed.job.sys.schedule.service.ScheduleConvert;
import com.yeungzhy.yeed.job.sys.schedule.service.ScheduleService;
import com.yeungzhy.yeed.job.sys.schedule.service.ScheduleSorts;
import com.yeungzhy.yeed.job.sys.schedule.vo.SchedulePageVO;
import com.yeungzhy.yeed.job.sys.schedule.vo.ScheduleVO;
import com.yeungzhy.yeed.job.sys.schedule.quartz.ScheduleLoader;
import com.yeungzhy.yeed.job.sys.schedule.quartz.ScheduleRuntime;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 定时任务定义表 服务实现类
 *
 * <p>数据库是调度的唯一真源：一律先写库再同步 Quartz，同步失败只记日志，
 * 下次启动装载会自愈；反向（先同步再写库）则可能留下库中不存在、却一直在跑的作业
 *
 * @author yeungzhy
 * @since 2026-09-02 23:52:19
 */
@Slf4j
@Service
public class ScheduleServiceImpl implements ScheduleService {

    @Resource
    private ScheduleSorts scheduleSorts;
    @Resource
    private ScheduleMapper scheduleMapper;
    @Resource
    private ScheduleConvert scheduleConvert;
    @Resource
    private ScheduleRegistry scheduleRegistry;
    @Resource
    private ScheduleLoader scheduleLoader;
    @Resource
    private ScheduleTargetValidator scheduleTargetValidator;


    @Override
    public Long save(ScheduleDTO dto) {
        // DTO -> Entity：同名字段由 MapStruct 自动映射
        Schedule entity = scheduleConvert.toEntity(dto);
        // 纠正后写回再校验：库里存的是真实 Bean 名，触发时直接可用
        entity.setBeanName(scheduleTargetValidator.resolveBeanName(entity.getBeanName()));
        scheduleTargetValidator.validate(entity);
        checkNameUnique(entity);
        scheduleMapper.insert(entity);
        scheduleRegistry.upsert(entity);
        return entity.getId();
    }


    @Override
    public void update(ScheduleDTO dto) {
        BizAssert.notNull(dto.getId(), "ID 不能为空");
        // DTO -> Entity：id 与业务字段均自动映射
        Schedule entity = scheduleConvert.toEntity(dto);
        entity.setBeanName(scheduleTargetValidator.resolveBeanName(entity.getBeanName()));
        scheduleTargetValidator.validate(entity);
        checkNameUnique(entity);
        scheduleMapper.updateById(entity);
        scheduleRegistry.upsert(entity);
    }


    @Override
    public ScheduleVO detail(Long id) {
        Schedule entity = scheduleMapper.selectById(id);
        BizAssert.notNull(entity, "记录不存在");
        // Entity -> VO：同名字段由 MapStruct 自动映射
        return fillRuntime(scheduleConvert.toVO(entity), entity);
    }


    @Override
    public SchedulePageVO page(SchedulePageDTO dto) {
        LambdaQueryWrapper<Schedule> lambdaQuery = Wrappers.<Schedule>lambdaQuery()
                .eq(dto.getGroupCode() != null, Schedule::getGroupCode, dto.getGroupCode())
                .eq(dto.getStatus() != null, Schedule::getStatus, dto.getStatus())
                .like(StringUtils.hasText(dto.getName()), Schedule::getName, dto.getName());

        // 应用排序：先单字段 → 再多字段(顺序敏感)；默认降序；白名单外字段静默忽略
        scheduleSorts.applyAll(lambdaQuery, dto.getOrderField(), dto.getIsAsc(), dto.getOrders());

        // 先取实体分页再转换：触发时间要按实体查 Quartz，转换后再回查实体会退化成 N+1
        PageResult<Schedule> entityPage = scheduleMapper.selectPageResult(dto, lambdaQuery);
        List<ScheduleVO> records = entityPage.getRecords().stream()
                .map(entity -> fillRuntime(scheduleConvert.toVO(entity), entity))
                .toList();
        return new SchedulePageVO()
                .setPage(PageResult.of(entityPage.getPageNum(), entityPage.getPageSize(), entityPage.getTotal(), records))
                .setStandby(scheduleRegistry.isStandby());
    }


    @Override
    public void delete(Long id) {
        BizAssert.notNull(id, "ID 不能为空");
        Schedule entity = scheduleMapper.selectById(id);
        BizAssert.notNull(entity, "记录不存在");
        // 先注销调度再删库：反过来的话，注销失败会留下"库里没了、Quartz 里还在"的隐形孤儿，
        // 而且启动装载只做覆盖不做清理，孤儿永远无法被发现
        BizAssert.isTrue(scheduleRegistry.remove(entity), "注销 Quartz 调度失败，记录未删除");
        scheduleMapper.physicalDeleteById(id);
    }


    @Override
    public void delete(Collection<Long> ids) {
        BizAssert.notEmpty(ids, "ID 集合不能为空");
        List<Schedule> entities = scheduleMapper.selectBatchIds(ids);
        // 只删注销成功的：部分失败时留下可见的待重试记录，好过制造孤儿
        List<Long> removed = entities.stream()
                .filter(scheduleRegistry::remove)
                .map(Schedule::getId)
                .toList();
        if (!removed.isEmpty()) {
            scheduleMapper.physicalDelete(Wrappers.<Schedule>lambdaQuery().in(Schedule::getId, removed));
        }
        BizAssert.isTrue(removed.size() == entities.size(), "部分计划注销失败，已保留对应记录");
    }


    @Override
    public int reloadAll() {
        return scheduleLoader.reloadAll();
    }


    @Override
    public void reloadOne(Long id) {
        Schedule entity = scheduleMapper.selectById(id);
        BizAssert.notNull(entity, "记录不存在");
        scheduleLoader.reloadOne(entity);
    }


    @Override
    public void changeStatus(Long id, EnableStatusEnum status) {
        Schedule entity = scheduleMapper.selectById(id);
        BizAssert.notNull(entity, "记录不存在");
        if (entity.getStatus() == status) {
            return;
        }
        // 只更新状态列
        Schedule updating = Schedule.builder()
                .id(id)
                .status(status)
                .version(entity.getVersion())
                .build();
        scheduleMapper.updateById(updating);
        entity.setStatus(status);
        scheduleRegistry.changeStatus(entity);
    }


    @Override
    public void triggerOnce(Long id) {
        Schedule entity = scheduleMapper.selectById(id);
        BizAssert.notNull(entity, "记录不存在");
        scheduleTargetValidator.validate(entity);
        scheduleRegistry.triggerOnce(entity);
    }


    @Override
    public List<LocalDateTime> previewFireTimes(ScheduleFireTimePreviewDTO dto) {
        CronExpression cron = parseCron(dto.getCronExpr());
        List<LocalDateTime> times = new ArrayList<>(dto.getCount());
        // 从"现在"开始推：下一次触发才是运维关心的，历史上的点火时间没有预览价值
        Date next = cron.getNextValidTimeAfter(new Date());
        for (int i = 0; i < dto.getCount() && next != null; i++) {
            times.add(LocalDateTime.ofInstant(next.toInstant(), ZoneId.systemDefault()));
            next = cron.getNextValidTimeAfter(next);
        }
        return times;
    }


    @Override
    public void changeStandby(boolean standby) {
        scheduleRegistry.changeStandby(standby);
    }


    /**
     * 解析 CRON 表达式，非法时按业务异常抛出
     *
     * <p>{@link CronExpression#isValidExpression(String)} 与构造器是两套校验，
     * 先 isValid 再构造只是为了拿到受检异常之外的统一业务话术
     */
    private CronExpression parseCron(String cronExpr) {
        BizAssert.isTrue(CronExpression.isValidExpression(cronExpr), "CRON 表达式非法");
        try {
            return new CronExpression(cronExpr);
        } catch (ParseException e) {
            throw new BizException("CRON 表达式非法");
        }
    }

    /**
     * 填充上次与下次触发时间
     */
    private ScheduleVO fillRuntime(ScheduleVO vo, Schedule entity) {
        ScheduleRuntime runtime = scheduleRegistry.runtime(entity);
        return vo.setPreviousFireTime(runtime.previousFireTime()).setNextFireTime(runtime.nextFireTime());
    }


    /**
     * 校验任务名称在分组内唯一
     */
    private void checkNameUnique(Schedule schedule) {
        Boolean exists = scheduleMapper.existsByWrapper(Wrappers.<Schedule>lambdaQuery()
                .eq(Schedule::getGroupCode, schedule.getGroupCode())
                .eq(Schedule::getName, schedule.getName())
                .ne(schedule.getId() != null, Schedule::getId, schedule.getId()));
        BizAssert.isTrue(!exists, "任务名称在分组内已存在");
    }


}
