package com.yeungzhy.yeed.job.export.engine.api;

import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.handler.WriteHandler;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.yeungzhy.yeed.api.export.ExportTypeEnum;
import com.yeungzhy.yeed.job.export.engine.ExportTaskEngine;
import com.yeungzhy.yeed.job.export.engine.ExporterRegistry;
import com.yeungzhy.yeed.job.export.engine.style.BackgroundImageWatermarkHandler;
import com.yeungzhy.yeed.job.export.engine.style.AdaptiveColumnWidthHandler;
import com.yeungzhy.yeed.job.export.engine.style.EnableStatusConverter;
import com.yeungzhy.yeed.job.export.engine.style.HeaderFooterWatermarkHandler;
import org.apache.poi.ss.usermodel.*;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Excel 导出器：每种导出类型一个实现，向 {@link ExportTaskEngine} 交代「导出什么、长什么样」
 *
 * <p> 导出器只负责业务侧：查什么数据、表头是什么、要不要脱敏；流程编排、分批写盘、上传 OSS
 * 与状态回写全部由引擎负责
 *
 * <p> 接入方式：实现本接口并标注 {@code @Component} 即可，{@link ExporterRegistry} 在启动时
 * 按 {@link #getExportType()} 自动收集并路由
 *
 * <p> 入参一律是 {@link ExportContext}：任务实体不会传到这里，本接口也不认识它。
 * 查询参数 {@code P} 已由装配点按 {@link #getParamType()} 反序列化完毕，导出器直接用即可
 *
 * <p> 生命周期钩子的调用顺序由引擎保证，实现方可依赖（标注「引擎动作」的两步非钩子、不可覆盖）：
 * <ol>
 *   <li>{@link #beforeExport}：参数校验、预热缓存</li>
 *   <li>{@link #totalCount}：既作进度分母，也是分页模式的循环终止条件</li>
 *   <li>循环「查询 → 写盘 → {@link #onBatchWritten}」直至数据取尽，每写完一批必触发一次回调(含收尾的不足批批次)</li>
 *   <li>{@link #beforeUpload}：拿到完整 xlsx 字节，可加密、追加签名页</li>
 *   <li>引擎上传 OSS → 引擎回写任务成功状态</li>
 *   <li>{@link #afterExport}：清理临时资源、发通知、记审计日志</li>
 * </ol>
 *
 * <p> 异常分支：以上任一环节抛出异常，后续步骤全部跳过，转入 {@link #onError}（只做清理与日志），
 * 再由引擎统一回写失败状态并吞掉异常（不外抛），导出器不应自行改状态，避免两处回写互相覆盖
 *
 * <p> 失败兜底的两段式边界（装配期发生在引擎的 {@code try} 之外，引擎包不到，只能由装配点自兜）：
 * <ul>
 *   <li>认领与装配（实体加载、JSON → P 解析）：由装配点 catch → 回写失败</li>
 *   <li>引擎管线（{@link #beforeExport} 起）：由引擎 catch → {@link #onError} → 回写失败 → 上抛</li>
 * </ul>
 *
 * @param <P> 业务查询参数类型，由 {@link #getParamType()} 声明
 * @param <R> Excel 行类型
 * @author yeungzhy
 * @since 2026-08-22
 * @see ExportTaskEngine
 * @see ExporterRegistry
 * @see ExportContext
 */
public interface Exporter<P, R> {

    // ============ 类型与查询模式 ========================

    /**
     * 本导出器对应的导出类型
     *
     * @return 导出类型枚举，不能为 null
     */
    ExportTypeEnum getExportType();

    /**
     * 本导出器使用的查询模式，决定引擎走流式还是分页循环
     *
     * @return 查询模式，不能为 null
     */
    QueryMode getQueryMode();

    /**
     * 业务查询参数类型：装配点据此把任务的查询参数 JSON 反序列化为强类型 P
     *
     * <p> 无查询条件的导出（如导出全量字典）返回 {@code Void.class}，装配点据此跳过解析，
     * 此时 {@link ExportContext#param()} 为 null
     *
     * <p> 只支持 {@code Class}：参数是泛型容器（如 {@code List<X>}）时本签名无法表达，
     * 届时另加 {@code TypeReference} 重载，不要在这里返回裸 {@code List.class} 再强转
     *
     * @return 参数类型，不能为 null
     */
    Class<P> getParamType();

    /**
     * 查询模式：两种模式产出的文件完全一致，差异只在取数方式与代价，按 SQL 形态选择即可
     */
    enum QueryMode {

        /**
         * 流式：导出器逐行回吐，内存占用与数据量无关，也没有深翻页问题
         *
         * <p> 取数方式由导出器自选，但次序硬要求与之一致：
         * 结果集次序必须唯一确定，{@code ORDER BY} 的末列应是主键等唯一列，
         * 排序不唯一时数据库不保证行的稳定次序，会出现漏行或重复行
         */
        STREAM,

        /**
         * 分页：按 pageNum / pageSize 反复查询，兼容无法游标化的复杂 SQL（多表 join、group by 等）
         *
         * <p> 代价是深翻页：第 N 页需扫描并丢弃前 N-1 页，数据量越大尾页越慢
         */
        PAGE
    }

    // ============ 数据查询 ========================

    /**
     * 查询本次导出的总行数
     *
     * <p> 在写文件前一次性调用，既作进度分母，也是分页模式的循环终止条件
     *
     * @param ctx 导出上下文
     * @return 总行数，不能为 null，无数据请返回 0
     */
    Long totalCount(ExportContext<P> ctx);

    /**
     * 分页查询 1 页数据（{@link QueryMode#PAGE} 模式必须重写）
     *
     * @param ctx      导出上下文
     * @param pageNum  页码，从 1 开始递增
     * @param pageSize 每页行数
     * @return 本页数据；返回空列表表示已无数据，引擎据此提前结束循环
     * @throws UnsupportedOperationException 默认实现直接抛出，声明 PAGE 模式却未重写时会在导出时才发现
     */
    default List<R> pageQuery(ExportContext<P> ctx, int pageNum, int pageSize) {
        throw new UnsupportedOperationException("此导出器未实现分页查询");
    }

    /**
     * 流式查询，把「一行」交给引擎回调（{@link QueryMode#STREAM} 模式必须重写）
     *
     * <p> 取数方式不限，三条硬要求：
     * <ul>
     *   <li>切勿先查全量 List 再遍历，那样流式就只剩"不分页"，省内存的意义全失</li>
     *   <li>若用 MyBatis 游标，{@code @Transactional} 必须加在本方法上：游标随 SqlSession 关闭而失效，
     *       遍历必须发生在事务边界内，把事务加在引擎侧（遍历的调用方）则等于没有保护；</li>
     *   <li>MySQL 游标流式期间独占连接：本方法内（含 {@link #onBatchWritten}）不能再用同一连接执行其他 SQL，
     *       否则抛 {@code Streaming result set ... is still active}。引擎的进度回写同理，
     *       需要时应让它走 {@code REQUIRES_NEW} 拿独立连接</li>
     * </ul>
     *
     * @param ctx         导出上下文
     * @param rowConsumer 行回调，引擎在其中累积到批次阈值后统一写盘
     * @throws UnsupportedOperationException 默认实现直接抛出，声明 STREAM 模式却未重写时会在导出时才发现
     */
    default void streamQuery(ExportContext<P> ctx, Consumer<R> rowConsumer) {
        throw new UnsupportedOperationException("此导出器未实现流式查询");
    }

    // ============ Sheet name 以及表头 ========================

    /**
     * Sheet 名称
     *
     * <p> 只有首个 Sheet 用本名；行数超限自动拆分出的后续 Sheet 由引擎追加 {@code _2}、{@code _3} 后缀
     *
     * @param ctx 导出上下文
     * @return Sheet 名称，不能为 null
     */
    default String getSheetName(ExportContext<P> ctx) {
        return "Sheet1";
    }

    /**
     * 固定表头类：EasyExcel 读取其字段上的 {@code @ExcelProperty} 生成表头
     *
     * <p> 与动态头互斥，动态头非空时本方法的返回值被忽略
     *
     * @param ctx 导出上下文
     * @return 表头类，不能为 null
     */
    Class<R> getFixedHead(ExportContext<P> ctx);

    /**
     * 动态表头：列在运行时才能确定时（如按前端勾选列导出）重写本方法
     *
     * <p> 外层 List 是行、内层是该行的列，返回多行即为多级表头
     *
     * @param ctx 导出上下文
     * @return 表头结构；返回空列表表示沿用 {@link #getFixedHead} 的类头
     */
    default List<List<String>> getDynamicHead(ExportContext<P> ctx) {
        return Collections.emptyList();
    }

    // ======================== 样式 ========================

    /**
     * 注册额外的 EasyExcel 写处理器（字典翻译、脱敏、下拉框、合并单元格等）
     *
     * <p> 收 {@code String} 而非 {@code Supplier<String>}：默认实现本就立即取水印文本，
     * 惰性从未被利用，多包一层只是噪音
     *
     * @param register  注册入口，每调一次 {@code accept} 注册一个处理器
     * @param watermark 水印文本；为 null 或空白时水印处理器静默跳过绘制（创建人未知时不铺空水印）
     */
    default void registerExtraWriteHandlers(Consumer<WriteHandler> register, String watermark) {
        register.accept(new AdaptiveColumnWidthHandler());
        register.accept(new BackgroundImageWatermarkHandler(watermark));
        register.accept(new HeaderFooterWatermarkHandler(watermark));
    }

    /**
     * 默认的表头与内容样式策略
     *
     * @return 样式策略
     */
    default HorizontalCellStyleStrategy getDefaultWriteStrategy() {
        // 表头样式：宝蓝色底 + 白字
        WriteCellStyle headStyle = getHeadWriteCellStyle();

        // 内容样式给两个：EasyExcel 按行轮换，实现隔行异色
        WriteCellStyle contentStyle = getContentWriteCellStyle();
        // 复刻一份内容样式再改背景，保证隔行两行的字体与对齐完全一致
        WriteCellStyle contentAltStyle = getContentWriteCellStyle();
        contentAltStyle.setFillPatternType(FillPatternType.DIAMONDS);

        // 浅灰斑马纹 #C0C0C0（GREY_25_PERCENT，亮度 0.74）：观感最稳；浅矢车菊蓝 #CCCCFF 试用偏亮，弃用
        contentAltStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());

        return new HorizontalCellStyleStrategy(headStyle, List.of(contentStyle, contentAltStyle));
    }


    /**
     * 注册默认的类型转换器（EasyExcel 写单元格时按类型匹配的转换规则）
     *
     * @param builder EasyExcel writer 构建器（转换器注册入口）
     */
    default void registerDefaultConverter(ExcelWriterBuilder builder) {
        builder.registerConverter(new EnableStatusConverter());
    }


    // --- 样式零件（供实现类复用或逐个重写） ---

    /**
     * 表头单元格样式：宝蓝色底 + 白字
     *
     * @return 表头样式，不能为 null
     */
    default WriteCellStyle getHeadWriteCellStyle() {
        WriteFont headFont = getDefaultFont(true);
        headFont.setColor(IndexedColors.WHITE.getIndex());

        WriteCellStyle headStyle = new WriteCellStyle();
        headStyle.setWriteFont(headFont);
        headStyle.setFillPatternType(FillPatternType.SOLID_FOREGROUND);
        headStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());

        // 水平/垂直居中 + 自动换行
        headStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        headStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headStyle.setWrapped(true);

        headStyle.setBorderTop(BorderStyle.THIN);
        headStyle.setBorderLeft(BorderStyle.THIN);
        headStyle.setBorderBottom(BorderStyle.THIN);
        headStyle.setBorderRight(BorderStyle.THIN);

        return headStyle;
    }

    /**
     * 内容单元格样式
     *
     * @return 内容样式，不能为 null
     */
    default WriteCellStyle getContentWriteCellStyle() {
        WriteCellStyle contentStyle = new WriteCellStyle();
        contentStyle.setWriteFont(getDefaultFont(false));

        contentStyle.setBorderTop(BorderStyle.THIN);
        contentStyle.setBorderLeft(BorderStyle.THIN);
        contentStyle.setBorderBottom(BorderStyle.THIN);
        contentStyle.setBorderRight(BorderStyle.THIN);

        // 垂直居中 + 自动换行, 水平对齐让 Excel 按数据类型自动决定
        contentStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        contentStyle.setWrapped(true);

        return contentStyle;
    }

    /**
     * 默认字体（黑体 10 号）
     *
     * @param bold 是否加粗（表头传 true）
     * @return 字体，不能为 null
     */
    default WriteFont getDefaultFont(boolean bold) {
        WriteFont font = new WriteFont();
        font.setFontName("黑体");
        font.setFontHeightInPoints((short) 10);
        font.setBold(bold);
        return font;
    }

    // ======================== 生命周期钩子 ========================

    /**
     * 导出开始前回调（校验参数、预热缓存等），在 {@link #totalCount} 之前执行
     *
     * @param ctx 导出上下文
     */
    default void beforeExport(ExportContext<P> ctx) { }

    /**
     * 每批数据写入 Excel 后回调
     *
     * <p> 可用于统计已处理行数、记录明细日志、推送实时进度到 MQ 等。
     * 两种查询模式下每批都会触发，含不足批次阈值的收尾批次，在此累计计数不会漏掉最后一批
     *
     * @param ctx            导出上下文
     * @param processedCount 已处理总行数
     * @param totalCount     {@link #totalCount} 的返回值
     */
    default void onBatchWritten(ExportContext<P> ctx, long processedCount, long totalCount) { }

    /**
     * 数据查完、Excel 写完、上传 OSS 前回调（可对最终字节做加密、追加签名页）
     *
     * @param ctx        导出上下文
     * @param excelBytes 完整的 xlsx 文件字节
     * @return 处理后的字节；不修改则原样返回入参
     */
    default byte[] beforeUpload(ExportContext<P> ctx, byte[] excelBytes) {
        return excelBytes;
    }

    /**
     * 上传 OSS 成功、任务状态回写完成后回调（清理临时资源、发通知、记审计日志等）
     *
     * @param ctx   导出上下文
     * @param ossId OSS 文件记录主键（{@code yeed_sys_oss.id}）
     */
    default void afterExport(ExportContext<P> ctx, Long ossId) { }

    /**
     * 导出异常回调
     *
     * <p> 这里只做资源清理与日志记录：任务状态由 {@link ExportTaskEngine} 统一回写失败，
     * 导出器无需也不应自己改状态，避免两处回写互相覆盖
     *
     * <p> 引擎先落库失败状态再回调本方法，本回调内抛出的异常被引擎隔离（只记日志），
     * 不影响已回写的失败状态与失败原因
     *
     * @param ctx 导出上下文
     * @param e   导致导出失败的异常
     */
    default void onError(ExportContext<P> ctx, Exception e) { }

}
