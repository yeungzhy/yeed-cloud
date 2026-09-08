package com.yeungzhy.yeed.job.export.schedule;

import com.yeungzhy.yeed.common.core.support.JacksonUtil;
import com.yeungzhy.yeed.job.export.engine.ExportTaskEngine;
import com.yeungzhy.yeed.job.export.engine.ExporterRegistry;
import com.yeungzhy.yeed.job.export.engine.api.ExportContext;
import com.yeungzhy.yeed.job.export.engine.api.Exporter;
import com.yeungzhy.yeed.job.sys.export.task.entity.ExportTask;
import com.yeungzhy.yeed.job.sys.export.task.mapper.ExportTaskMapper;
import com.yeungzhy.yeed.job.sys.export.task.service.ExportTaskService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 导出任务轮询作业（导出任务装配点）
 *
 * <p> 职责：分批认领待执行任务（每批至多 {@value #BATCH_SIZE} 个，事务内原子置 RUNNING），
 * 并发执行本批全部任务并等待完成后，再认领下一批，直至当前积压清空。
 * 失败兜底遵循「引擎外 / 引擎内」边界：引擎外（路由 / 装配）失败由此处回写 FAILED，
 * 引擎期失败由引擎自行回写并吞掉异常，本类不再重复回写
 *
 * <p> 触发方式：本类由调度框架按周期调用 {@link #poll()}（将来经 sys/schedule 定时计划
 * 反射执行本 Bean 的 poll 方法）；目前调度壳尚未接入，由 DebugApi 手动触发同一方法
 *
 * <p> 并发说明：同一任务只可能被认领一次，认领在 {@link ExportTaskService#claimRunning(int)}
 * 事务内完成（FOR UPDATE + CAS），多实例/调度重叠时互不重复；批内任务并发执行，批间串行等待。
 * 任务一旦认领即视为已开始执行，异常中断（进程崩溃等）不自动回退 WAITING（崩溃自愈属调度未来能力）
 *
 * @author yeungzhy
 * @since 2026-09-07
 */
@Slf4j
@Component
public class ExportTaskPollingJob {

    /** 认领批大小 = 单批并发上限：每批至多认领 3 个任务整体并跑，批间串行等待 */
    private static final int BATCH_SIZE = 3;

    /** 批内任务执行器：虚拟线程、每任务一条虚拟线程 */
    private static final ExecutorService EXECUTOR = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("export-task-runner-", 0).factory());

    @Resource
    private ExportTaskService exportTaskService;
    @Resource
    private ExportTaskEngine exportTaskEngine;
    @Resource
    private ExportTaskMapper exportTaskMapper;
    @Resource
    private ExporterRegistry exporterRegistry;


    /**
     * 轮询执行待执行（WAITING）的导出任务，直至当前积压清空
     *
     * <p> 每轮：认领至多 {@value #BATCH_SIZE} 个 → 并发执行 → 等待本批全部完成 → 认领下一批
     *
     * <p> 单批内任一任务失败不阻塞同批其余任务，也不中断后续批次
     * <p> 若将来调度要求「每个触发周期只处理一批」，把本方法的循环换成单次调用即可
     */
    public void poll() {
        log.debug("导出任务轮询开始");

        while (true) {
            List<ExportTask> batch = exportTaskService.claimRunning(BATCH_SIZE);
            if (batch.isEmpty()) {
                log.info("导出任务轮询结束，本次认领待导出任务数=0");
                return;
            }

            // 并发执行已认领的多个任务
            List<CompletableFuture<Void>> futures = batch.stream()
                    .map(task -> CompletableFuture.runAsync(() -> runOne(task), EXECUTOR))
                    .toList();

            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (CompletionException e) {
                /*
                 * runOne 内部已兜底（引擎外失败回写 FAILED、引擎期失败由引擎回写并吞异常）
                 * 正常情况下不会走到这里，防御性捕获：任何单个任务的意外逃逸都不应中断后续批次
                 */
                log.error("导出任务批次执行异常中断，本批任务数={}", batch.size(), e);
            }
        }
    }


    /**
     * 执行单个任务：路由 → 装配 → 引擎
     *
     * <p> 路由、装配失败时任务状态尚未回写，统一由本方法补写 FAILED；
     * 引擎期失败由引擎自行回写（onError + updateFailed）并吞掉异常、正常不外抛。
     * 异常若逃逸到此，只可能是引擎兜底路径本身出错的极端情形（失败状态未必已落库），此处兜底补写
     *
     * @param task 已认领（RUNNING）的任务
     */
    private void runOne(ExportTask task) {
        try {
            Exporter<?, ?> exporter = exporterRegistry.getExporter(task.getExportType());
            dispatch(task, exporter);
        } catch (Exception e) {
            // 引擎外失败（路由 / 装配），或引擎兜底路径逃逸（失败原因未可靠回写）：由此补写 FAILED
            writeFailed(task, e);
        }
    }

    /**
     * 装配上下文并交给引擎执行
     *
     * <p> 装配失败（参数反序列化、上下文校验）不在此处理，冒泡给 {@link #runOne} 统一回写；
     * 引擎执行失败由引擎自理（回写 + 吞异常），正常不外抛，无需在此兜底
     *
     * @param task     已认领（RUNNING）的任务
     * @param exporter 按导出类型路由到的导出器
     */
    private <P, H> void dispatch(ExportTask task, Exporter<P, H> exporter) {
        P param = parseParam(task, exporter.getParamType());
        ExportContext<P> ctx = new ExportContext<>(task.getId(), task.getFileName(), task.waterMarkText(), param);
        exportTaskEngine.execute(ctx, exporter);
    }

    /**
     * 把任务的查询参数 JSON 反序列化为导出器声明的类型
     *
     * <p> {@code param} 为 null 必须在此拦下：空值会一路带到导出器里触发 NPE，
     * 报错信息完全看不出根因是「任务没存查询参数」
     *
     * @param task      已认领（RUNNING）的任务
     * @param paramType 导出器自报的参数类型；{@code Void.class} 表示无查询条件
     * @return 查询参数；无查询条件（{@code Void.class}）的导出返回 null，其余情况保证非 null，导出器据此可以放心使用，无需自行判空
     */
    private <P> P parseParam(ExportTask task, Class<P> paramType) {
        if (Void.class.equals(paramType)) {
            return null;
        }
        if (!StringUtils.hasText(task.getQueryParam())) {
            throw new IllegalArgumentException("任务缺少查询参数 queryParam，无法装配为 " + paramType.getName());
        }
        P param = JacksonUtil.parseObject(task.getQueryParam(), paramType);
        if (param == null) {
            /*
             * queryParam 是 JSON 字面量 null 时反序列化结果也为空，与「没存参数」后果一样
             * 空值会一路带到导出器里触发 NPE，且报错看不出根因，故在此一并拦下
             */
            throw new IllegalArgumentException("任务查询参数 queryParam 解析结果为空，无法装配为 " + paramType.getName());
        }
        return param;
    }

    /**
     * 回写失败状态（引擎外失败兜底）
     *
     * <p> 服务两类「失败原因尚未可靠回写」的失败：引擎外的路由 / 装配失败，以及引擎兜底路径逃逸
     * 的极端情形（失败状态未必已落库）。引擎正常失败由引擎自行回写并吞异常，不会走到这里
     *
     * @param task 已认领（RUNNING）的任务
     * @param e    失败原因
     */
    private void writeFailed(ExportTask task, Exception e) {
        log.error("导出任务失败，taskId={}", task.getId(), e);
        exportTaskMapper.updateFailed(task.getId(), e.getMessage());
    }

}
