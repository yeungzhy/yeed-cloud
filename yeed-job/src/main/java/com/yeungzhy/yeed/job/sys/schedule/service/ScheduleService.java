package com.yeungzhy.yeed.job.sys.schedule.service;

import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import com.yeungzhy.yeed.job.sys.schedule.dto.ScheduleDTO;
import com.yeungzhy.yeed.job.sys.schedule.dto.ScheduleFireTimePreviewDTO;
import com.yeungzhy.yeed.job.sys.schedule.dto.SchedulePageDTO;
import com.yeungzhy.yeed.job.sys.schedule.vo.SchedulePageVO;
import com.yeungzhy.yeed.job.sys.schedule.vo.ScheduleVO;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 定时任务定义表 服务类
 *
 * @author yeungzhy
 * @since 2026-09-02 23:52:19
 */
public interface ScheduleService {

    // ==================== 标准写入（CUD） ====================

    /**
     * 新增
     *
     * @param dto 入参
     * @return 新增记录的主键 ID
     * @author yeungzhy
     * @since 2026-09-02 23:52:19
     */
    Long save(ScheduleDTO dto);

    /**
     * 更新
     *
     * @param dto 入参
     * @author yeungzhy
     * @since 2026-09-02 23:52:19
     */
    void update(ScheduleDTO dto);


    // ==================== 调度控制（同步数据库与 Quartz） ====================

    /**
     * 切换启停，停用走 Quartz 暂停而非注销，重新启用可接续原有触发时间
     *
     * @param id     计划主键
     * @param status 目标状态
     * @author yeungzhy
     * @since 2026-09-08
     */
    void changeStatus(Long id, EnableStatusEnum status);

    /**
     * 立即执行一次，不影响原有 CRON 触发器
     *
     * <p>维护模式下人工触发仍然放行：它是运维的显式动作，与"阻止自动触发"不是一回事
     *
     * @param id 计划主键
     * @author yeungzhy
     * @since 2026-09-08
     */
    void triggerOnce(Long id);

    /**
     * 预览 CRON 表达式接下来的若干次触发时间
     *
     * @param dto 表达式与预览条数
     * @return 触发时间列表，表达式永不触发时为空列表
     * @author yeungzhy
     * @since 2026-09-08
     */
    List<LocalDateTime> previewFireTimes(ScheduleFireTimePreviewDTO dto);

    /**
     * 切换全局维护模式，进入后全部实例停止自动触发
     *
     * @param standby true 进入维护模式，false 恢复
     * @author yeungzhy
     * @since 2026-09-08
     */
    void changeStandby(boolean standby);

    // ==================== 手动同步（生产无法重启时的修复入口） ====================

    /**
     * 按当前库数据重新装载全部计划，等价于重启一次装载
     *
     * <p>生产环境重启需要审批，库与 Quartz 出现不一致时用它兜底，不必重启进程
     *
     * @return 装载条数
     * @author yeungzhy
     * @since 2026-09-08
     */
    int reloadAll();

    /**
     * 重新装载单条计划，用于单条数据被手工修正后同步到 Quartz
     *
     * @param id 计划主键
     * @author yeungzhy
     * @since 2026-09-08
     */
    void reloadOne(Long id);


    // ==================== 标准查询（R） ====================

    /**
     * 详情
     *
     * @param id 主键 ID
     * @return 详情数据
     * @author yeungzhy
     * @since 2026-09-02 23:52:19
     */
    ScheduleVO detail(Long id);

    /**
     * 分页查询
     *
     * @param dto 分页查询入参
     * @return 分页结果
     * @author yeungzhy
     * @since 2026-09-02 23:52:19
     */
    SchedulePageVO page(SchedulePageDTO dto);


    // ==================== 删除（物理删除 + 注销调度） ====================

    /**
     * 删除（物理删除，不可恢复），同时注销 Quartz 侧的作业与触发器
     *
     * @param id 主键 ID
     * @author yeungzhy
     * @since 2026-09-02 23:52:19
     */
    void delete(Long id);

    /**
     * 批量删除（物理删除，不可恢复），逐个注销 Quartz 侧的作业与触发器
     *
     * @param ids 主键 ID 集合
     * @author yeungzhy
     * @since 2026-09-02 23:52:19
     */
    void delete(Collection<Long> ids);

}
