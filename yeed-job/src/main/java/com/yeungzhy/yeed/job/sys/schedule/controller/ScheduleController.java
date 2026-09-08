package com.yeungzhy.yeed.job.sys.schedule.controller;

import com.yeungzhy.yeed.common.core.request.IdRequest;
import com.yeungzhy.yeed.common.core.request.IdsRequest;
import com.yeungzhy.yeed.common.core.request.StatusRequest;
import com.yeungzhy.yeed.common.core.result.ApiResult;
import com.yeungzhy.yeed.job.sys.schedule.dto.ScheduleFireTimePreviewDTO;
import com.yeungzhy.yeed.job.sys.schedule.dto.ScheduleStandbyDTO;
import com.yeungzhy.yeed.job.sys.schedule.dto.ScheduleDTO;
import com.yeungzhy.yeed.job.sys.schedule.dto.SchedulePageDTO;
import com.yeungzhy.yeed.job.sys.schedule.service.ScheduleService;
import com.yeungzhy.yeed.job.sys.schedule.vo.SchedulePageVO;
import com.yeungzhy.yeed.job.sys.schedule.vo.ScheduleVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务定义表 前端控制器
 *
 * @author yeungzhy
 * @since 2026-09-02 23:52:19
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/sys/schedule")
public class ScheduleController {

    @Resource
    private ScheduleService scheduleService;


    /**
     * 新增
     *
     * @param dto 入参
     * @return 新增记录的主键 ID
     * @author yeungzhy
     * @since 2026-09-02 23:52:19
     */
    @PostMapping("/save")
    public ApiResult<Long> save(@Valid @RequestBody ScheduleDTO dto) {
        return ApiResult.ok(scheduleService.save(dto));
    }


    /**
     * 更新
     *
     * @param dto 入参
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-09-02 23:52:19
     */
    @PostMapping("/update")
    public ApiResult<Boolean> update(@Valid @RequestBody ScheduleDTO dto) {
        scheduleService.update(dto);
        return ApiResult.ok();
    }


    /**
     * 详情
     *
     * @param id 主键 ID
     * @return 详情数据
     * @author yeungzhy
     * @since 2026-09-02 23:52:19
     */
    @PostMapping("/detail")
    public ApiResult<ScheduleVO> detail(@Valid @RequestBody IdRequest id) {
        return ApiResult.ok(scheduleService.detail(id.getId()));
    }


    /**
     * 分页查询，同时返回全局维护模式状态
     *
     * @param dto 分页查询入参
     * @return 分页结果与维护模式开关
     * @author yeungzhy
     * @since 2026-09-02 23:52:19
     */
    @PostMapping("/page")
    public ApiResult<SchedulePageVO> page(@Valid @RequestBody SchedulePageDTO dto) {
        return ApiResult.ok(scheduleService.page(dto));
    }


    /**
     * 启停切换，停用后不再触发，重新启用接续原有触发时间
     *
     * @param req 主键 ID 与目标状态
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-09-08
     */
    @PostMapping("/change-status")
    public ApiResult<Boolean> changeStatus(@Valid @RequestBody StatusRequest req) {
        scheduleService.changeStatus(req.getId(), req.getStatus());
        return ApiResult.ok();
    }


    /**
     * 立即执行一次，不影响原有 CRON 触发器
     *
     * @param id 主键 ID
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-09-08
     */
    @PostMapping("/trigger-once")
    public ApiResult<Boolean> triggerOnce(@Valid @RequestBody IdRequest id) {
        scheduleService.triggerOnce(id.getId());
        return ApiResult.ok();
    }


    /**
     * 删除（物理删除，不可恢复）并注销 Quartz 侧的作业
     *
     * @param id 主键 ID
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-09-08
     */
    @PostMapping("/delete")
    public ApiResult<Boolean> delete(@Valid @RequestBody IdRequest id) {
        scheduleService.delete(id.getId());
        return ApiResult.ok();
    }


    /**
     * 批量删除（物理删除，不可恢复）并逐个注销 Quartz 侧的作业
     *
     * @param req 主键 ID 集合
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-09-08
     */
    @PostMapping("/delete-batch")
    public ApiResult<Boolean> deleteBatch(@Valid @RequestBody IdsRequest req) {
        scheduleService.delete(req.getIds());
        return ApiResult.ok();
    }


    /**
     * 预览 CRON 表达式接下来的触发时间
     *
     * <p>由后端解析：Quartz 的 CRON 与通用 CRON 库语义不同，前端自行解析会给出错误承诺
     *
     * @param dto 表达式与预览条数
     * @return 触发时间列表
     * @author yeungzhy
     * @since 2026-09-08
     */
    @PostMapping("/preview-firetimes")
    public ApiResult<List<LocalDateTime>> previewFireTimes(@Valid @RequestBody ScheduleFireTimePreviewDTO dto) {
        return ApiResult.ok(scheduleService.previewFireTimes(dto));
    }


    /**
     * 切换全局维护模式，进入后全部实例停止自动触发，人工触发不受影响
     *
     * @param dto 维护模式开关
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-09-08
     */
    @PostMapping("/change-standby")
    public ApiResult<Boolean> changeStandby(@Valid @RequestBody ScheduleStandbyDTO dto) {
        scheduleService.changeStandby(dto.getStandby());
        return ApiResult.ok();
    }


    /**
     * 按当前库数据重新装载全部计划
     *
     * <p>库与 Quartz 不一致时的修复入口，效果等同重启一次，用于生产不便重启的场景
     *
     * @return 装载条数
     * @author yeungzhy
     * @since 2026-09-08
     */
    @PostMapping("/reload-all")
    public ApiResult<Integer> reloadAll() {
        return ApiResult.ok(scheduleService.reloadAll());
    }


    /**
     * 重新装载单条计划，用于手工修正库数据后同步到 Quartz
     *
     * @param id 计划主键
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-09-08
     */
    @PostMapping("/reload-one")
    public ApiResult<Boolean> reloadOne(@Valid @RequestBody IdRequest id) {
        scheduleService.reloadOne(id.getId());
        return ApiResult.ok();
    }


}
