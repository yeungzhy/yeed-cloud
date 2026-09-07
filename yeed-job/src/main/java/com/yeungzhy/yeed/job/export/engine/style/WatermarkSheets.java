package com.yeungzhy.yeed.job.export.engine.style;

import com.alibaba.excel.write.handler.context.SheetWriteHandlerContext;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;

/**
 * 水印处理器的 sheet 解包：把 EasyExcel 交给处理器的 sheet 统一解到 {@link XSSFSheet}
 *
 * <p>为什么 SXSSF 流式模式（即不 {@code inMemory(true)}）同样能挂水印：SXSSF 的写出不是重造一份 workbook，
 * 而是「内部 XSSFWorkbook 先整包写出成模板 → 拿刷出的行替换其中的 {@code <sheetData>} 段」。
 * 水印依赖的元素恰好全部落在替换范围之外：
 * <ul>
 *   <li>页眉 {@code <headerFooter>}、{@code <legacyDrawingHF>}、背景 {@code <picture>}
 *       在 CTWorksheet 的元素序列里都排在 {@code <sheetData>} <b>之后</b>；</li>
 *   <li>VML 部件与图片是独立 OPC 部件，随模板整包复制出去。</li>
 * </ul>
 * 所以只要挂到内部 XSSFSheet 上即可随成品落盘。SXSSFSheet 只实现 {@link Sheet} 接口、不暴露 OPC 关系
 * 与 CTWorksheet，故经 {@link SXSSFWorkbook#getXSSFWorkbook()} 取内部 workbook 再取同名 sheet，不用反射。
 *
 * @author yeungzhy
 * @since 2026-09-08
 */
final class WatermarkSheets {

    private WatermarkSheets() {
    }

    /**
     * 解包到可挂 OPC 关系与 CTWorksheet 的 {@link XSSFSheet}
     *
     * <p>SXSSF 下返回的是「模板 sheet」：写出时只有它的 sheetData 会被替换，其余元素与关系原样保留。
     *
     * @param context EasyExcel sheet 回调上下文
     * @return 内部 XSSFSheet
     * @throws IllegalStateException 非 xlsx（如 HSSF）导出，或 SXSSF 下取不到同名内部 sheet
     */
    static XSSFSheet unwrap(SheetWriteHandlerContext context) {
        Sheet sheet = context.getWriteSheetHolder().getSheet();
        // 先判 SXSSFSheet：它拿不到关系与 CTWorksheet，必须换成内部 sheet（与 XSSF 可能有继承关系，故前置判断）
        if (sheet instanceof SXSSFSheet sxssfSheet
                && sxssfSheet.getWorkbook() instanceof SXSSFWorkbook sxssfWorkbook) {
            XSSFSheet innerSheet = sxssfWorkbook.getXSSFWorkbook().getSheet(sxssfSheet.getSheetName());
            if (innerSheet != null) {
                return innerSheet;
            }
            throw new IllegalStateException("SXSSF 模式下取不到同名内部 sheet，无法挂载水印");
        }
        if (sheet instanceof XSSFSheet xssfSheet) {
            return xssfSheet;
        }
        throw new IllegalStateException("水印仅支持 xlsx 导出（XSSF / SXSSF）");
    }

}
