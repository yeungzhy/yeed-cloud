package com.yeungzhy.yeed.job.export.engine;

import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.handler.WriteHandler;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.yeungzhy.yeed.api.export.ExportTypeEnum;
import com.yeungzhy.yeed.job.sys.export.task.entity.ExportTask;
import org.apache.ibatis.session.ResultHandler;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Excel 导出执行器：每种导出类型一个实现，向 {@link ExcelExportEngine} 交代「导出什么、长什么样」
 *
 * <p>职责边界：执行器只负责业务侧——查什么数据、表头是什么、要不要脱敏；
 * 分批写盘、进度推进、Sheet 拆分、上传 OSS、任务状态回写一律由引擎承担，执行器不要碰。
 *
 * <p>接入方式：实现本接口并标注 {@code @Component} 即可，{@link ExportExecutorRegistry} 在启动时
 * 按 {@link #getExportType()} 自动收集并路由，无需任何手工注册代码。
 *
 * <p>生命周期钩子的调用顺序由引擎保证，实现方可依赖：
 * <ul>
 *   <li>{@link #beforeExport} → {@link #totalCount} → 循环[{@code 查询 → 写盘 → }{@link #onBatchWritten}]；</li>
 *   <li>→ {@link #beforeUpload} → 上传 OSS → 回写成功 → {@link #afterExport}；</li>
 *   <li>任一环节抛异常 → {@link #onError} → 回写失败状态。</li>
 * </ul>
 *
 * @param <R> 行数据类型：一次查询返回的单行对象，由 {@link #pageQuery} / {@link #streamQuery} 产出
 * @param <H> 表头类：EasyExcel 据其字段上的注解生成表头，见 {@link #getFixedHead}
 *
 * @author yeungzhy
 * @since 2026-08-22
 * @see ExcelExportEngine
 * @see ExportExecutorRegistry
 */
public interface ExportExecutor<R, H> {

    // ============ 类型与查询模式 ========================

    /**
     * 本执行器对应的导出类型
     *
     * <p>取值必须来自 {@link ExportTypeEnum}——它是类型 key 的唯一真相源，
     * {@link ExportExecutorRegistry} 直接按该枚举的 {@link ExportTypeEnum#getKey()} 建立路由表。
     *
     * @return 导出类型枚举，不能为 null
     */
    ExportTypeEnum getExportType();

    /**
     * 本执行器使用的查询模式，决定引擎走流式还是分页循环
     *
     * @return 查询模式，不能为 null
     */
    QueryMode getQueryMode();

    /**
     * 查询模式：两种模式产出的文件完全一致，差异只在取数方式与代价，按 SQL 形态选择即可
     */
    enum QueryMode {

        /**
         * 流式：MyBatis 游标逐行回吐，内存占用与数据量无关，也没有深翻页问题
         *
         * <p>硬要求：{@code ORDER BY} 的末列必须是主键等唯一确定列——排序不唯一时数据库不保证行的稳定次序，
         * 游标推进会出现漏行或重复行，且只在数据量大时偶发，极难复现
         */
        STREAM,

        /**
         * 分页：按 pageNum / pageSize 反复查询，兼容无法游标化的复杂 SQL（多表 join、group by 等）
         *
         * <p>代价是深翻页：第 N 页需扫描并丢弃前 N-1 页，数据量越大尾页越慢
         */
        PAGE
    }

    // ============ 数据查询 ========================

    /**
     * 查询本次导出的总行数
     *
     * <p>在写文件前一次性调用，既作进度分母，也是分页模式的循环终止条件。
     *
     * @param task 待执行任务，查询参数取自 {@link ExportTask#getQueryParam()}
     * @return 总行数，不能为 null——引擎直接把它用于数值比较与除法（拆箱），返回 null 会在导出中抛 NPE；
     *         无数据请返回 0（引擎导出空文件而不是静默失败）
     */
    Long totalCount(ExportTask task);

    /**
     * 分页查询一页数据（{@link QueryMode#PAGE} 模式必须重写）
     *
     * @param task     待执行任务
     * @param pageNum  页码，从 1 开始递增
     * @param pageSize 每页行数
     * @return 本页数据；返回空列表表示已无数据，引擎据此提前结束循环
     * @throws UnsupportedOperationException 默认实现直接抛出——声明 PAGE 模式却未重写，会在导出时才发现
     */
    default List<R> pageQuery(ExportTask task, int pageNum, int pageSize) {
        throw new UnsupportedOperationException("此执行器未实现分页查询");
    }

    /**
     * 流式查询，通过游标逐行回吐（{@link QueryMode#STREAM} 模式必须重写）
     *
     * <p>实现必须用 MyBatis 的游标能力（Mapper 方法返回 {@code Cursor}，或使用 {@link ResultHandler}），
     * 切勿先查全量 List 再遍历——那样流式就只剩"不分页"，省内存的意义全失。
     *
     * @param task    待执行任务
     * @param handler 行回调，引擎在其中累积到批次阈值后统一写盘
     * @throws UnsupportedOperationException 默认实现直接抛出——声明 STREAM 模式却未重写，会在导出时才发现
     */
    default void streamQuery(ExportTask task, ResultHandler<R> handler) {
        throw new UnsupportedOperationException("此执行器未实现流式查询");
    }

    // ============ 表头 ========================

    /**
     * 固定表头类：EasyExcel 读取其字段上的 {@code @ExcelProperty} 生成表头
     *
     * <p>与 {@link #getDynamicHead} 互斥，动态头非空时本方法的返回值被忽略。
     *
     * @param task 待执行任务
     * @return 表头类，不能为 null
     */
    Class<H> getFixedHead(ExportTask task);

    /**
     * 动态表头：列在运行时才能确定时（如按前端勾选列导出）重写本方法
     *
     * <p>外层 List 是行、内层是该行的列，返回多行即为多级表头。
     *
     * @param task 待执行任务
     * @return 表头结构；返回空列表表示沿用 {@link #getFixedHead} 的类头
     */
    default List<List<String>> getDynamicHead(ExportTask task) {
        return Collections.emptyList();
    }

    // ============ 样式与 Sheet ========================

    /**
     * 注册额外的 EasyExcel 写处理器（字典翻译、脱敏、下拉框、合并单元格等）
     *
     * <p>引擎把 builder 的注册方法作为消费者传入，实现方只需调用 {@code accept}，
     * 无需接触 {@link com.alibaba.excel.write.builder.ExcelWriterBuilder}。
     * <pre>{@code
     * public void registerExtraWriteHandlers(Consumer<WriteHandler> register) {
     *     register.accept(new DictConvertHandler());
     *     register.accept(new DataMaskHandler());
     * }
     * }</pre>
     *
     * @param register 注册入口，每调一次 {@code accept} 注册一个处理器
     */
    default void registerExtraWriteHandlers(Consumer<WriteHandler> register) {
        register.accept(new LongestMatchColumnWidthStyleStrategy());
    }

    /**
     * 默认的表头与内容样式策略（表头浅蓝加粗、内容白 / 浅灰隔行）
     *
     * <p>方法名是单数、返回单个对象：EasyExcel 的 {@link HorizontalCellStyleStrategy}
     * 内部已支持多个内容样式轮换，不需要也不应该返回集合。
     *
     * @return 样式策略，不能为 null
     */
    default HorizontalCellStyleStrategy getDefaultWriteStrategy() {
        // 表头样式：宝蓝色底 + 白字
        WriteCellStyle headStyle = getHeadWriteCellStyle();

        // 内容样式给两个：EasyExcel 按行轮换，实现隔行异色
        WriteCellStyle contentStyle = getContentWriteCellStyle();
        // 复刻一份内容样式再改背景，保证隔行两行的字体与对齐完全一致
        WriteCellStyle contentAltStyle = getContentWriteCellStyle();
        contentAltStyle.setFillPatternType(FillPatternType.SOLID_FOREGROUND);

        /*
         * 方案 B: 浅矢车菊蓝  RGB: #CCCCFF  亮度 0.83
         * contentAltStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
         */

        // 方案 A: 经典浅灰斑马纹 (最稳妥)  RGB: #C0C0C0  亮度 0.74
        contentAltStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());

        return new HorizontalCellStyleStrategy(headStyle, List.of(contentStyle, contentAltStyle));
    }

    /**
     * Sheet 名称
     *
     * <p>只有首个 Sheet 用本名；行数超限自动拆分出的后续 Sheet 由引擎追加 {@code _2}、{@code _3} 后缀。
     *
     * @param task 待执行任务
     * @return Sheet 名称，不能为 null
     */
    default String getSheetName(ExportTask task) {
        return "Sheet1";
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

    // ============ 生命周期钩子 ========================

    /**
     * 导出开始前回调（校验参数、预热缓存等），在 {@link #totalCount} 之前执行
     *
     * @param task 待执行任务
     */
    default void beforeExport(ExportTask task) {
    }

    /**
     * 每批数据写入 Excel 后回调
     *
     * <p>可用于统计已处理行数、记录明细日志、推送实时进度到 MQ 等。
     * 注意：流式模式下不足批次阈值的收尾批次不会触发本回调。
     *
     * @param task           待执行任务
     * @param processedCount 已处理总行数
     * @param totalCount     {@link #totalCount} 的返回值
     */
    default void onBatchWritten(ExportTask task, long processedCount, long totalCount) {
    }

    /**
     * 数据查完、Excel 写完、上传 OSS 前回调（可对最终字节做加密、加水印、追加签名页）
     *
     * @param task       待执行任务
     * @param excelBytes 完整的 xlsx 文件字节
     * @return 处理后的字节；不修改则原样返回入参
     */
    default byte[] beforeUpload(ExportTask task, byte[] excelBytes) {
        return excelBytes;
    }

    /**
     * 上传 OSS 成功、任务状态回写完成后回调（清理临时资源、发通知、记审计日志等）
     *
     * @param task  待执行任务
     * @param ossId OSS 文件记录主键（{@code yeed_sys_oss.id}）
     */
    default void afterExport(ExportTask task, Long ossId) {
    }

    /**
     * 导出异常回调
     *
     * <p>这里只做资源清理与日志记录：任务状态由 {@link ExcelExportEngine} 统一回写失败，
     * 执行器无需也不应自己改状态，避免两处回写互相覆盖。
     *
     * @param task 待执行任务
     * @param e    导致导出失败的异常
     */
    default void onError(ExportTask task, Exception e) {
    }

}
