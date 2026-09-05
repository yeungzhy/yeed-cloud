package com.yeungzhy.yeed.job.export.engine;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.yeungzhy.yeed.api.export.ExportTypeEnum;
import com.yeungzhy.yeed.api.oss.OssFileFeignClient;
import com.yeungzhy.yeed.api.oss.dto.FileUploadQuery;
import com.yeungzhy.yeed.job.sys.export.task.entity.ExportTask;
import com.yeungzhy.yeed.job.sys.export.task.mapper.ExportTaskMapper;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.FastByteArrayOutputStream;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel 导出执行引擎：驱动单个任务跑完「查数 → 写 Excel → 上传 OSS → 回写任务状态」
 *
 * <p>流程在引擎内固定，可变部分全部委托给 {@link ExportExecutor}（每种导出类型一个实现）：
 * 引擎负责分批写盘、进度节流、Sheet 拆分、上传 OSS 与状态回写，执行器只提供数据、表头、样式与生命周期钩子。
 *
 * <p>进度区间：写文件阶段占用 [10, 90]，按已处理行数线性推进；上传 OSS 期间停在 90，
 * 成功后由 {@link ExportTaskMapper#updateSuccess} 统一置 100。上传是单次网络调用、无中间进度可报，
 * 留 10% 余量是为了让「上传中」与「已写完」在进度条上可区分。
 *
 * <p>失败语义：任一环节抛出异常，先回调 {@link ExportExecutor#onError} 让执行器清理资源，再回写失败原因，
 * 最后原样向上抛出——异常不吞，由调度侧决定重试与告警。
 *
 * <p>查询模式由 {@link ExportExecutor#getQueryMode()} 决定，两种模式的取舍见 {@link #writeByStream} 与 {@link #writeByPage}。
 *
 * @author yeungzhy
 * @since 2026-08-22
 * @see ExportExecutor
 * @see ExportExecutorRegistry
 */
@Component
public class ExcelExportEngine {

    // 写盘与分页
    /** 流式模式下累积多少行刷一次 Excel。纯写盘批次，与查询分页无关 */
    private static final int WRITE_BATCH_SIZE = 5000;
    // TODO(yeungzhy): PAGE_SIZE 小于 WRITE_BATCH_SIZE（1000 < 5000），翻页查询串行执行，取数耗时随数据量线性增长；改为多线程并发查页，按页号有序写盘以保证行序稳定
    /** 分页模式下每次查询的行数。调大减少往返、调小降低单次内存与锁持有时间 */
    private static final int PAGE_SIZE = 1000;
    /** 单个 Sheet 的行数上限，超出自动新建 Sheet */
    private static final int SHEET_MAX_ROWS = 100000;

    // 进度百分比
    /** 写文件阶段的起始百分比：0-10 留给任务认领与前置校验 */
    private static final int PROGRESS_START_PERCENT = 10;
    /** 写文件阶段的区间跨度：10 起步 + 80 跨度 = 90 封顶 */
    private static final int PROGRESS_RANGE_PERCENT = 80;
    /** 写文件阶段的封顶百分比：上传 OSS 成功后才置 100 */
    private static final int PROGRESS_CAP_PERCENT = 90;
    /** 进度回写阈值：变化不足 5% 不写库，避免大文件导出时高频 UPDATE 同一行 */
    private static final int PROGRESS_THRESHOLD = 5;

    @Resource
    private ExportTaskMapper exportTaskMapper;
    @Resource
    private OssFileFeignClient ossFileFeignClient;

    /**
     * 执行一次导出任务
     *
     * <p>全流程串行执行：成功则回写成功状态并带上 ossId 与文件大小；
     * 任一环节失败则回写失败原因，并原样抛出原异常交由调度侧重试。
     *
     * @param task     待执行任务，须已落库且 {@code id} 非空
     * @param executor 与 {@link ExportTask#getExportType()} 匹配的执行器，由 {@link ExportExecutorRegistry} 取得
     * @throws RuntimeException 查询 / 写盘 / 上传任一环节失败时原样抛出（失败状态已先行回写）
     */
    public <R, H> void execute(ExportTask task, ExportExecutor<R, H> executor) {
        try {
            executor.beforeExport(task);

            byte[] excelData;
            if (executor.getQueryMode() == ExportExecutor.QueryMode.STREAM) {
                excelData = writeByStream(task, executor);
            } else {
                excelData = writeByPage(task, executor);
            }

            // 上传前钩子可以整体替换字节（加密、水印、追加签名页），故必须用返回值覆盖原引用
            excelData = executor.beforeUpload(task, excelData);

            FileUploadQuery metadata = new FileUploadQuery()
                    .setBizCode(ExportTypeEnum.USER_EXPORT.name())
                    .setFileName(task.getFileName());
            // RPC-Style：upload 契约裸返回 OSS 记录主键（Long），失败抛异常中断
            Long ossId = ossFileFeignClient.upload(metadata, excelData);
            exportTaskMapper.updateSuccess(task.getId(), ossId, excelData.length);

            executor.afterExport(task, ossId);
        } catch (Exception e) {
            // 先给执行器清理资源的机会，再回写失败状态；异常继续上抛，由调度侧决定是否重试
            executor.onError(task, e);
            exportTaskMapper.updateFailed(task.getId(), e.getMessage());
            throw e;
        }
    }

    /**
     * 流式查询模式：MyBatis 游标逐行回吐，内存占用与数据量无关，也没有深翻页问题
     *
     * <p>代价是查询期间一直持有数据库游标连接，且 SQL 必须唯一确定排序，
     * 硬要求见 {@link ExportExecutor#streamQuery}。
     *
     * @param task     待执行任务
     * @param executor 执行器
     * @return 完整的 xlsx 文件字节
     */
    private <R, H> byte[] writeByStream(ExportTask task, ExportExecutor<R, H> executor) {
        Long total = executor.totalCount(task);

        FastByteArrayOutputStream out = new FastByteArrayOutputStream();
        ExcelWriterBuilder builder = EasyExcel.write(out).registerWriteHandler(executor.getDefaultWriteStrategy());
        configureHead(builder, executor, task);
        executor.registerExtraWriteHandlers(builder::registerWriteHandler);

        try (ExcelWriter writer = builder.build()) {
            WriteSheet sheet = EasyExcel.writerSheet(executor.getSheetName(task)).build();

            List<R> buffer = new ArrayList<>(WRITE_BATCH_SIZE);
            // 游标回调是 lambda，局部变量需 effectively final，故用数组承载可变计数
            int[] processed = {0};
            int[] lastPercent = {PROGRESS_START_PERCENT};

            executor.streamQuery(task, context -> {
                buffer.add(context.getResultObject());
                if (buffer.size() < WRITE_BATCH_SIZE) {
                    return;
                }
                writer.write(buffer, sheet);
                processed[0] += buffer.size();
                reportProgress(task, processed[0], total, lastPercent);
                executor.onBatchWritten(task, processed[0], total);
                buffer.clear();
            });

            // 收尾批次凑不满 WRITE_BATCH_SIZE，回调内不会触发写入，必须在此补写
            if (!buffer.isEmpty()) {
                writer.write(buffer, sheet);
            }
            // 显式收尾落盘（表尾、样式、临时资源），必须在取字节之前完成
            writer.finish();
        }

        // toByteArray 直接返回内部缓冲区、不做拷贝；此时 writer 已关闭，数据已全部 flush
        return out.toByteArray();
    }

    /**
     * 分页查询模式：按页反复查询，兼容无法游标化的复杂 SQL（多表 join、group by 等）
     *
     * <p>代价是深翻页：第 N 页需扫描并丢弃前 N-1 页，数据量越大尾页越慢。
     * 单 Sheet 行数达到 {@code SHEET_MAX_ROWS} 时自动切到下一个 Sheet。
     *
     * <p>循环终止条件是 {@code processed >= total}：total 在写文件前一次性查得，
     * 期间新增的数据不会被导出——优先保证不死循环，而不是导出增量。
     *
     * @param task     待执行任务
     * @param executor 执行器
     * @return 完整的 xlsx 文件字节
     */
    private <R, H> byte[] writeByPage(ExportTask task, ExportExecutor<R, H> executor) {
        Long total = executor.totalCount(task);

        FastByteArrayOutputStream out = new FastByteArrayOutputStream();
        ExcelWriterBuilder builder = EasyExcel.write(out).registerWriteHandler(executor.getDefaultWriteStrategy());
        configureHead(builder, executor, task);
        executor.registerExtraWriteHandlers(builder::registerWriteHandler);

        try (ExcelWriter writer = builder.build()) {
            int pageNum = 1;
            int processed = 0;
            // 与流式模式同构：数组承载，共用同一个进度回写方法
            int[] lastPercent = {PROGRESS_START_PERCENT};
            int sheetNo = 0;
            int rowNumInSheet = 0;
            WriteSheet sheet = null;

            while (processed < total) {
                List<R> pageData = executor.pageQuery(task, pageNum, PAGE_SIZE);
                if (pageData.isEmpty()) {
                    break;
                }

                // 同一 ExcelWriter 内直接建新 WriteSheet 即可切表，无需手动 finish 上一个
                if (sheet == null || rowNumInSheet >= SHEET_MAX_ROWS) {
                    String sheetName = executor.getSheetName(task) + (sheetNo == 0 ? "" : "_" + (sheetNo + 1));
                    sheet = EasyExcel.writerSheet(sheetNo, sheetName).build();
                    sheetNo++;
                    rowNumInSheet = 0;
                }

                writer.write(pageData, sheet);
                processed += pageData.size();
                rowNumInSheet += pageData.size();

                reportProgress(task, processed, total, lastPercent);
                executor.onBatchWritten(task, processed, total);

                pageNum++;
            }
            // 显式收尾落盘（表尾、样式、临时资源），必须在取字节之前完成
            writer.finish();
        }

        return out.toByteArray();
    }

    // ============ 内部辅助方法 / 避免漂移方法 ========================

    // --- 进度 ---

    /**
     * 按需回写进度：变化不足 {@code PROGRESS_THRESHOLD} 时跳过，避免大文件导出时高频 UPDATE 同一行
     *
     * <p>到达封顶值 {@code PROGRESS_CAP_PERCENT} 时无条件写一次，保证进度条能走到 90 再进入上传阶段。
     *
     * @param task        待执行任务
     * @param processed   已处理行数
     * @param total       总行数，可为 null
     * @param lastPercent 上次回写的百分比；单次元素数组，便于在流式回调中就地更新
     */
    private void reportProgress(ExportTask task, int processed, Long total, int[] lastPercent) {
        int percent = calcProgress(processed, total);
        if (percent - lastPercent[0] >= PROGRESS_THRESHOLD || percent >= PROGRESS_CAP_PERCENT) {
            exportTaskMapper.updateProgress(task.getId(), percent);
            lastPercent[0] = percent;
        }
    }

    /**
     * 计算写文件阶段的进度百分比
     *
     * <p>返回值恒落在 [10, 90]：total 为 null 或 0 时按 1 归一，既避免除零，也让空数据导出直接给出封顶值。
     *
     * @param processed 已处理行数
     * @param total     总行数，可为 null
     * @return 进度百分比
     */
    private int calcProgress(int processed, Long total) {
        long safeTotal = Math.max(total == null ? 0L : total, 1L);
        int percent = PROGRESS_START_PERCENT + (int) (processed * PROGRESS_RANGE_PERCENT / safeTotal);
        return Math.min(percent, PROGRESS_CAP_PERCENT);
    }

    // --- Excel 装配 ---

    /**
     * 配置表头：动态头优先，类头兜底
     *
     * <p>二者互斥——{@link ExcelWriterBuilder#head(Class)} 与 {@code head(List)} 后写覆盖先写，
     * 故必须二选一，不能无条件连调。
     *
     * @param builder  待配置的表头构造器
     * @param executor 执行器
     * @param task     待执行任务
     */
    private <R, H> void configureHead(ExcelWriterBuilder builder, ExportExecutor<R, H> executor, ExportTask task) {
        List<List<String>> dynamicHead = executor.getDynamicHead(task);
        if (CollectionUtils.isNotEmpty(dynamicHead)) {
            builder.head(dynamicHead);
        } else {
            builder.head(executor.getFixedHead(task));
        }
    }

}
