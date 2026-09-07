package com.yeungzhy.yeed.job.export.engine;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.yeungzhy.yeed.api.oss.OssFileFeignClient;
import com.yeungzhy.yeed.api.oss.dto.FileUploadQuery;
import com.yeungzhy.yeed.job.export.engine.api.ExportContext;
import com.yeungzhy.yeed.job.export.engine.api.Exporter;
import com.yeungzhy.yeed.job.sys.export.task.mapper.ExportTaskMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.util.FastByteArrayOutputStream;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Excel 导出执行引擎：驱动单个任务跑完「查数 → 写 Excel → 上传 OSS → 回写任务状态」
 *
 * <p>流程在引擎内固定，可变部分全部委托给 {@link Exporter}（每种导出类型一个实现）：
 * 引擎负责分批写盘、进度节流、Sheet 拆分、上传 OSS 与状态回写，导出器只提供数据、表头、样式与生命周期钩子。
 *
 * <p>引擎不认识任务实体：入口只收 {@link ExportContext}，回写一律走 {@link ExportTaskMapper} 的定向
 * update（只 UPDATE 需要的列，而非整行覆盖），既避免旧快照覆盖他人修改，也让引擎与实体解耦。
 *
 * <p>进度区间：写文件阶段占用 [10, 90]，按已处理行数线性推进；上传 OSS 期间停在 90，
 * 成功后由 {@link ExportTaskMapper#updateSuccess} 统一置 100。上传是单次网络调用、无中间进度可报，
 * 留 10% 余量是为了让「上传中」与「已写完」在进度条上可区分。
 *
 * <p>失败语义：任一环节抛出异常，先记录日志、回写失败原因（主流程，不被后续动作阻断），
 * 再回调 {@link Exporter#onError} 让导出器清理资源——清理是不可信副动作，单独兜底：失败只记日志，
 * 不影响已回写的失败状态与失败原因。失败状态既已落库、任务已按失败终结，异常不再向外抛——对装配点而言
 * 本类是失败链的终点，装配点无需感知引擎成败，也无需重复回写。重试 / 告警属调度侧未来能力（崩溃自愈），
 * 引擎内不处理。
 * <b>本语义只覆盖引擎管线</b>：{@link ExportContext} 的装配（实体加载、JSON → 参数反序列化）发生在
 * 本类的 try 之外，由装配点自行回写失败。
 *
 * <p>查询模式由 {@link Exporter#getQueryMode()} 决定，两种模式的取舍见 {@link #writeByStream} 与 {@link #writeByPage}。
 *
 * <p>可观测性：每个任务一条 {@link StopWatch}（每任务新建，非线程安全的类不做共享字段），
 * 跨「取总行数 → 写文件 → 上传 → 回写」分段计时，成功与失败都会打出阶段耗时分布，
 * 作为「取数慢还是写盘慢」这类判断的数据依据——本类的多次优化取舍最终都要回到这组数据上。
 *
 * @author yeungzhy
 * @since 2026-08-22
 * @see Exporter
 * @see ExporterRegistry
 * @see ExportContext
 */
@Slf4j
@Component
public class ExportTaskEngine {

    // 写盘与分页
    /** 流式模式下累积多少行刷一次 Excel。纯写盘批次，与查询分页无关 */
    private static final int WRITE_BATCH_SIZE = 5000;
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

    // 计时阶段名：StopWatch 的分段标识，也是阶段耗时汇总里的字段名
    private static final String STAGE_BEFORE_EXPORT = "前置钩子";
    private static final String STAGE_TOTAL_COUNT = "取总行数";
    private static final String STAGE_WRITE_FILE = "写文件";
    private static final String STAGE_BEFORE_UPLOAD = "上传前钩子";
    private static final String STAGE_UPLOAD = "上传OSS";
    private static final String STAGE_WRITE_SUCCESS = "回写成功";
    private static final String STAGE_AFTER_EXPORT = "收尾钩子";

    @Resource
    private ExportTaskMapper exportTaskMapper;
    @Resource
    private OssFileFeignClient ossFileFeignClient;

    /**
     * 执行一次导出任务
     *
     * <p>全流程串行执行：成功则回写成功状态并带上 ossId 与文件大小；
     * 任一环节失败则先记录日志、回写失败原因，再回调 onError 清理资源（清理失败被隔离、止于日志），
     * 失败状态已落库后本方法正常返回、不再抛异常。
     *
     * <p>全程用 {@link StopWatch} 分段计时：开始 / 结束各一条日志，取总行数、写文件、上传 OSS 三个
     * 关键节点（远程或重 IO）各自再打一条带耗时的日志；失败日志同样带已耗时与阶段分布，
     * 据此能直接看出任务卡在哪一段。
     *
     * @param ctx      导出上下文，由装配点从任务实体映射而来，{@code taskId} 非空
     * @param exporter 与本次导出类型匹配的导出器，由 {@link ExporterRegistry} 取得
     * @param <P>      业务查询参数类型
     * @param <H>      Excel 行类型
     */
    public <P, H> void execute(ExportContext<P> ctx, Exporter<P, H> exporter) {
        // 每任务一个 StopWatch：它非线程安全，绝不能做成共享字段；一次导出即一条线性流水线，正合分段计时
        StopWatch stopWatch = new StopWatch("export-" + ctx.taskId());
        log.info("导出任务开始，taskId={}，类型={}，模式={}，文件名={}",
                ctx.taskId(), exporter.getExportType(), exporter.getQueryMode(), ctx.fileName());
        try {
            stopWatch.start(STAGE_BEFORE_EXPORT);
            exporter.beforeExport(ctx);
            stopWatch.stop();

            stopWatch.start(STAGE_TOTAL_COUNT);
            Long total = exporter.totalCount(ctx);
            stopWatch.stop();
            log.info("导出任务取总行数完成，taskId={}，总行数={}，耗时={}ms",
                    ctx.taskId(), total, stopWatch.getLastTaskTimeMillis());

            byte[] excelData;
            stopWatch.start(STAGE_WRITE_FILE);
            if (exporter.getQueryMode() == Exporter.QueryMode.PAGE) {
                excelData = writeByPage(ctx, exporter, total);
            } else {
                excelData = writeByStream(ctx, exporter, total);
            }
            stopWatch.stop();
            log.info("导出任务写文件完成，taskId={}，总行数={}，字节={}，耗时={}ms",
                    ctx.taskId(), total, excelData.length, stopWatch.getLastTaskTimeMillis());

            // 上传前钩子可以整体替换字节（加密、追加签名页），故必须用返回值覆盖原引用
            stopWatch.start(STAGE_BEFORE_UPLOAD);
            excelData = exporter.beforeUpload(ctx, excelData);
            stopWatch.stop();

            FileUploadQuery metadata = new FileUploadQuery()
                    .setBizCode(exporter.getExportType().name())
                    .setFileName(ctx.fileName());
            // RPC-Style：upload 契约裸返回 OSS 记录主键（Long），失败抛异常中断
            // 现状 = 成品 byte[] 全量驻留 + Feign 编码器全量复制（峰值 ≈3×）；
            // 消除要靠「先写临时文件 + 流式上传（P4）」，见 docs/export-memory-design.md §3.2
            stopWatch.start(STAGE_UPLOAD);
            Long ossId = ossFileFeignClient.upload(metadata, excelData);
            stopWatch.stop();
            log.info("导出任务上传 OSS 完成，taskId={}，ossId={}，字节={}，耗时={}ms",
                    ctx.taskId(), ossId, excelData.length, stopWatch.getLastTaskTimeMillis());

            stopWatch.start(STAGE_WRITE_SUCCESS);
            exportTaskMapper.updateSuccess(ctx.taskId(), ossId, excelData.length);
            stopWatch.stop();

            stopWatch.start(STAGE_AFTER_EXPORT);
            exporter.afterExport(ctx, ossId);
            stopWatch.stop();

            log.info("导出任务执行成功，taskId={}，ossId={}，总行数={}，总耗时={}ms，阶段耗时：{}",
                    ctx.taskId(), ossId, total, stopWatch.getTotalTimeMillis(), summarize(stopWatch));
        } catch (Exception e) {
            // 兜底前先收尾计时：运行中的那段要计到此刻，否则失败日志里看不到卡在哪一段
            if (stopWatch.isRunning()) {
                stopWatch.stop();
            }
            // 主流程先行：日志与失败原因回写必须先落地，不得被任何可能再出错的副动作阻断；
            // 否则 onError 等清理逻辑一抛，日志与状态回写会被连带冲掉，任务将卡在 RUNNING 直到崩溃自愈
            log.error("导出任务执行失败，taskId={}，已耗时={}ms，阶段耗时：{}",
                    ctx.taskId(), stopWatch.getTotalTimeMillis(), summarize(stopWatch), e);
            exportTaskMapper.updateFailed(ctx.taskId(), e.getMessage());
            // 资源清理是不可信副动作，单独兜底、失败止于日志——若让它裸抛，异常会逃逸到装配点，
            // 拿清理异常覆盖上面刚回写的真实失败原因，也违背本引擎「失败链终点、不外抛」的契约
            try {
                exporter.onError(ctx, e);
            } catch (Exception ex) {
                log.error("导出器资源清理失败，taskId={}", ctx.taskId(), ex);
            }
        }
    }

    /**
     * 流式查询模式：导出器逐行回吐，内存占用与数据量无关，也没有深翻页问题
     *
     * <p>代价是取数期间一直持有底层游标（数据库连接 / 长连接），且结果集次序必须唯一确定，
     * 硬要求见 {@link Exporter#streamQuery}。
     *
     * @param ctx      导出上下文
     * @param exporter 导出器
     * @param total    总行数，由 {@link Exporter#totalCount} 在写文件前一次性查得
     * @return 完整的 xlsx 文件字节
     */
    private <P, H> byte[] writeByStream(ExportContext<P> ctx, Exporter<P, H> exporter, Long total) {
        FastByteArrayOutputStream out = new FastByteArrayOutputStream();
        ExcelWriterBuilder builder = newWriterBuilder(ctx, exporter, out);

        try (ExcelWriter writer = builder.inMemory(true).build()) {
            WriteSheet sheet = EasyExcel.writerSheet(exporter.getSheetName(ctx)).build();

            List<H> buffer = new ArrayList<>(WRITE_BATCH_SIZE);
            // 游标回调是 lambda，局部变量需 effectively final，故用数组承载可变计数
            int[] processed = {0};
            int[] lastPercent = {PROGRESS_START_PERCENT};

            // 满批次与收尾批次走同一段逻辑，避免收尾批次漏掉 onBatchWritten
            Runnable flush = () -> {
                int batchSize = buffer.size();
                writer.write(buffer, sheet);
                buffer.clear();
                processed[0] += batchSize;
                reportProgress(ctx, processed[0], total, lastPercent);
                exporter.onBatchWritten(ctx, processed[0], total);
            };

            exporter.streamQuery(ctx, row -> {
                buffer.add(row);
                if (buffer.size() >= WRITE_BATCH_SIZE) {
                    flush.run();
                }
            });

            // 收尾批次凑不满 WRITE_BATCH_SIZE，回调内不会触发写入，必须在此补写
            if (!buffer.isEmpty()) {
                flush.run();
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
     * 真正的解法是换取数方式——{@link Exporter.QueryMode#STREAM} 游标，或 keyset（{@code id > lastId}）
     * 顺序翻页（代价恒定、无深分页），而不是在本模式里叠并发：并发只压缩墙钟等待，
     * 不减少数据库总扫描量（各页 offset 不同），反而让下游瞬时并发翻数倍。
     * 单 Sheet 行数达到 {@code SHEET_MAX_ROWS} 时自动切到下一个 Sheet。
     *
     * <p>循环终止条件是 {@code processed >= total}：total 在写文件前一次性查得，
     * 期间新增的数据不会被导出——优先保证不死循环，而不是导出增量。
     *
     * @param ctx      导出上下文
     * @param exporter 导出器
     * @param total    总行数，由 {@link Exporter#totalCount} 在写文件前一次性查得
     * @return 完整的 xlsx 文件字节
     */
    private <P, H> byte[] writeByPage(ExportContext<P> ctx, Exporter<P, H> exporter, Long total) {
        FastByteArrayOutputStream out = new FastByteArrayOutputStream();
        ExcelWriterBuilder builder = newWriterBuilder(ctx, exporter, out);

        try (ExcelWriter writer = builder.inMemory(true).build()) {
            int pageNum = 1;
            int processed = 0;
            // 与流式模式同构：数组承载，共用同一个进度回写方法
            int[] lastPercent = {PROGRESS_START_PERCENT};
            int sheetNo = 0;
            int rowNumInSheet = 0;
            WriteSheet sheet = null;

            while (processed < total) {
                List<H> pageData = exporter.pageQuery(ctx, pageNum, PAGE_SIZE);
                if (pageData.isEmpty()) {
                    break;
                }

                // 同一 ExcelWriter 内直接建新 WriteSheet 即可切表，无需手动 finish 上一个
                if (sheet == null || rowNumInSheet >= SHEET_MAX_ROWS) {
                    String sheetName = exporter.getSheetName(ctx) + (sheetNo == 0 ? "" : "_" + (sheetNo + 1));
                    sheet = EasyExcel.writerSheet(sheetNo, sheetName).build();
                    sheetNo++;
                    rowNumInSheet = 0;
                }

                writer.write(pageData, sheet);
                processed += pageData.size();
                rowNumInSheet += pageData.size();

                reportProgress(ctx, processed, total, lastPercent);
                exporter.onBatchWritten(ctx, processed, total);

                pageNum++;
            }
            // 显式收尾落盘（表尾、样式、临时资源），必须在取字节之前完成
            writer.finish();
        }

        return out.toByteArray();
    }

    // ============ 内部辅助方法 / 避免漂移方法 ========================

    // --- 计时 ---

    /**
     * 把各阶段耗时拼成单行文本，供完成 / 失败日志一并打出
     *
     * <p>刻意不用 {@code StopWatch#prettyPrint()}：它是多行表格，日志按行采集时会散成多条、
     * 也拿不到 traceId，排查时反而难用。这里只拼已完成的分段——失败时运行中的那段同样在列
     * （调用方已先 {@code stop}），因此能看出任务卡在哪一段。
     *
     * @param stopWatch 计时器
     * @return 形如 {@code 取总行数=12ms, 写文件=3456ms} 的单行文本
     */
    private String summarize(StopWatch stopWatch) {
        StringJoiner joiner = new StringJoiner(", ");
        for (StopWatch.TaskInfo task : stopWatch.getTaskInfo()) {
            joiner.add(task.getTaskName() + "=" + task.getTimeMillis() + "ms");
        }
        return joiner.toString();
    }

    // --- 进度 ---

    /**
     * 按需回写进度：变化不足 {@code PROGRESS_THRESHOLD} 时跳过，避免大文件导出时高频 UPDATE 同一行
     *
     * <p>到达封顶值 {@code PROGRESS_CAP_PERCENT} 时无条件写一次，保证进度条能走到 90 再进入上传阶段。
     *
     * @param ctx         导出上下文
     * @param processed   已处理行数
     * @param total       总行数，可为 null
     * @param lastPercent 上次回写的百分比；单次元素数组，便于在流式回调中就地更新
     */
    private <P> void reportProgress(ExportContext<P> ctx, int processed, Long total, int[] lastPercent) {
        int percent = calcProgress(processed, total);
        if (percent - lastPercent[0] >= PROGRESS_THRESHOLD || percent >= PROGRESS_CAP_PERCENT) {
            exportTaskMapper.updateProgress(ctx.taskId(), percent);
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
     * 装配 {@link ExcelWriter} 的构造器：表头、默认样式与导出器注册的额外写处理器
     *
     * <p>两种查询模式产出的文件必须完全一致，装配顺序也就不能各写一份——表头（动态/固定二选一）、
     * 转换器、写处理器（水印等）在此统一注册，调用方只负责往里写数据。
     *
     * @param ctx      导出上下文
     * @param exporter 导出器
     * @param out      输出流，由调用方持有——写完后要从中取字节，故不在此封装
     * @return 已注册完毕的构造器
     */
    private <P, H> ExcelWriterBuilder newWriterBuilder(ExportContext<P> ctx, Exporter<P, H> exporter, FastByteArrayOutputStream out) {
        ExcelWriterBuilder builder = EasyExcel.write(out).registerWriteHandler(exporter.getDefaultWriteStrategy());
        configureHead(builder, exporter, ctx);
        exporter.registerDefaultConverter(builder);
        exporter.registerExtraWriteHandlers(builder::registerWriteHandler, ctx.watermarkText());
        return builder;
    }

    /**
     * 配置表头：动态头优先，类头兜底
     *
     * <p>二者互斥——{@link ExcelWriterBuilder#head(Class)} 与 {@code head(List)} 后写覆盖先写，
     * 故必须二选一，不能无条件连调。
     *
     * @param builder  待配置的表头构造器
     * @param exporter 导出器
     * @param ctx      导出上下文
     */
    private <P, H> void configureHead(ExcelWriterBuilder builder, Exporter<P, H> exporter, ExportContext<P> ctx) {
        List<List<String>> dynamicHead = exporter.getDynamicHead(ctx);
        if (CollectionUtils.isNotEmpty(dynamicHead)) {
            builder.head(dynamicHead);
        } else {
            builder.head(exporter.getFixedHead(ctx));
        }
    }

}
