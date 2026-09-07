package com.yeungzhy.yeed.job.export.engine.style;

import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.Head;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.util.DateUtils;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.style.column.AbstractColumnWidthStyleStrategy;
import org.apache.poi.ss.usermodel.Cell;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自适应列宽策略：按列内「最长内容的显示宽度」定宽
 *
 * <p>官方 {@link AbstractColumnWidthStyleStrategy} 估宽不准，本实现改按「显示宽度」估算：
 * <ul>
 *   <li>按显示宽度折算：半角算 1、全角（中日韩文字、全角标点等）算 2；</li>
 *   <li>数字去科学计数法与无效尾零（{@code stripTrailingZeros().toPlainString()}），按实际显示算宽；</li>
 *   <li>日期按单元格显示格式折算实际位数（默认 {@code yyyy-MM-dd HH:mm:ss}，支持 {@code @DateTimeFormat} 定制）；</li>
 *   <li>换行文本取最长一行，与折行后的实际显示一致；</li>
 *   <li>加留白 {@value #PADDING_UNITS}、保证最小列宽、设软上限（超长内容交自动换行，硬上限仍守 xlsx 规范的 255）。</li>
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-09-07
 */
public class AdaptiveColumnWidthHandler extends AbstractColumnWidthStyleStrategy {

    /** Excel 列宽硬上限（字符单位）：xlsx 规范就是 255 */
    private static final int HARD_MAX_COLUMN_WIDTH = 255;

    /** 额外留白兜住误差 */
    private static final int PADDING_UNITS = 2;

    /** 本实例生效的最小列宽 */
    private final int minWidth;
    /** 本实例生效的软上限列宽（会被 255 硬上限二次约束） */
    private final int maxWidth;

    /**
     * (sheetNo → (columnIndex → 已设置的最大列宽))，只增不减：绝大多数单元格只做一次 Map 比较，
     * 仅在最长记录被刷新时才回写 sheet，避免每个单元格都触发 POI 列宽写入
     */
    private final Map<Integer, Map<Integer, Integer>> widthCache = new HashMap<>(16);

    /**
     * 按默认最小列宽 8 与默认软上限 80 构造
     */
    public AdaptiveColumnWidthHandler() {
        this(8, 80);
    }

    /**
     * @param minWidth 最小列宽（字符单位），超长内容也不会低于此值
     * @param maxWidth 软上限列宽（字符单位），内容更长时靠自动换行消化，最大不超过 255
     */
    public AdaptiveColumnWidthHandler(int minWidth, int maxWidth) {
        this.minWidth = minWidth;
        this.maxWidth = Math.min(maxWidth, HARD_MAX_COLUMN_WIDTH);
    }

    /**
     * 单元格写完后回调：估算本格内容的显示宽度，超越列内最长记录时回写列宽
     *
     * <p>调用时机与官方一致（{@code afterCellDispose}），表头单元格与数据单元格都会进来。
     */
    @Override
    protected void setColumnWidth(WriteSheetHolder writeSheetHolder, List<WriteCellData<?>> cellDataList,
            Cell cell, Head head, Integer relativeRowIndex, Boolean isHead) {
        boolean needSetWidth = isHead || (cellDataList != null && !cellDataList.isEmpty());
        if (!needSetWidth) {
            return;
        }
        int contentWidth = measureWidth(cellDataList, cell, isHead);
        if (contentWidth < 0) {
            return;
        }
        int columnWidth = Math.min(Math.max(contentWidth + PADDING_UNITS, minWidth), maxWidth);

        Map<Integer, Integer> maxColumnWidthMap = widthCache.computeIfAbsent(
                writeSheetHolder.getSheetNo(), key -> new HashMap<>(16));
        Integer maxColumnWidth = maxColumnWidthMap.get(cell.getColumnIndex());
        if (maxColumnWidth == null || columnWidth > maxColumnWidth) {
            maxColumnWidthMap.put(cell.getColumnIndex(), columnWidth);
            writeSheetHolder.getSheet().setColumnWidth(cell.getColumnIndex(), columnWidth * 256);
        }
    }

    // ============ 内部辅助方法 ========================

    /**
     * 取单元格内容的「显示宽度」（字符单位）
     *
     * <p>表头直接读单元格文本（多级表头每列的 Head 只含自己的层级名，逐格回调天然逐层覆盖）。
     * 数据格按 {@link WriteCellData} 类型分派：字符串/布尔/数字/日期参与估宽，图片、公式等无法从值
     * 估宽的类型跳过（沿用官方行为，交由该列其他单元格或最小列宽兜底）。
     *
     * @return 显示宽度；无法估宽时返回 -1
     */
    private int measureWidth(List<WriteCellData<?>> cellDataList, Cell cell, boolean isHead) {
        String text;
        if (isHead) {
            text = cell.getStringCellValue();
        } else {
            WriteCellData<?> cellData = cellDataList.get(0);
            CellDataTypeEnum type = cellData.getType();
            if (type == null) {
                return -1;
            }
            switch (type) {
                case STRING:
                    text = cellData.getStringValue();
                    break;
                case BOOLEAN:
                    text = String.valueOf(cellData.getBooleanValue());
                    break;
                case NUMBER:
                    BigDecimal number = cellData.getNumberValue();
                    // 去科学计数法与无效尾零：toString 对大数/小数会产出远长于实际显示的字符串
                    text = number == null ? null : number.stripTrailingZeros().toPlainString();
                    break;
                case DATE:
                    return dateWidth(cellData);
                default:
                    return -1;
            }
        }
        return displayWidth(text);
    }

    /**
     * 估算 DATE 类型单元格按显示格式渲染后的宽度（字符单位）
     *
     * <p>显示格式取自单元格数据自带的格式信息（{@code @DateTimeFormat} 定制或 EasyExcel 默认值
     * {@code yyyy-MM-dd HH:mm:ss}）。取不到时按 EasyExcel 的默认日期格式兜底。
     */
    private static int dateWidth(WriteCellData<?> cellData) {
        String format = null;
        WriteCellStyle writeCellStyle = cellData.getWriteCellStyle();
        if (writeCellStyle != null && writeCellStyle.getDataFormatData() != null) {
            format = writeCellStyle.getDataFormatData().getFormat();
        }
        if (format == null || format.isEmpty()) {
            format = DateUtils.defaultDateFormat;
        }
        return displayWidth(expandDatePattern(format));
    }

    /**
     * 把日期格式模式串折算成实际显示文本：连续模式字母段的显示位数 = 段长
     * （{@code yyyy}→4 位、{@code MM}→2 位、{@code SSS}→3 位），其余字符原样显示。
     * {@code yyyy-MM-dd HH:mm:ss} 折算后即 19 字符宽的 {@code 0000-00-00 00:00:00}
     */
    private static String expandDatePattern(String format) {
        StringBuilder display = new StringBuilder(format.length());
        int i = 0;
        while (i < format.length()) {
            char current = format.charAt(i);
            if (isDatePatternLetter(current)) {
                int runEnd = i;
                while (runEnd < format.length() && format.charAt(runEnd) == current) {
                    runEnd++;
                }
                display.append("0".repeat(runEnd - i));
                i = runEnd;
            } else {
                display.append(current);
                i++;
            }
        }
        return display.toString();
    }

    /** 是否日期模式字母（SimpleDateFormat 语义：GyYMLwWDFdFEuHkKhamsS 等） */
    private static boolean isDatePatternLetter(char c) {
        return "GyYMLwWDFdFEuHkKhamsS".indexOf(c) >= 0;
    }

    /**
     * 文本的「显示宽度」（字符单位，与 Excel 列宽单位同构）：半角算 1、全角算 2
     *
     * <p>含换行时取最长一行——内容样式带自动换行，折行显示，按整串算会把列宽撑到没边。
     * 按码点遍历以正确处理增补平面字符（CJK 扩展 B 区等代理对）。
     */
    private static int displayWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int maxWidth = 0;
        for (String line : text.split("\r\n|\r|\n", -1)) {
            int width = 0;
            int i = 0;
            while (i < line.length()) {
                int codePoint = line.codePointAt(i);
                width += isFullWidth(codePoint) ? 2 : 1;
                i += Character.charCount(codePoint);
            }
            maxWidth = Math.max(maxWidth, width);
        }
        return maxWidth;
    }

    /**
     * 是否全角字符（East Asian Width 的 W / F 类）：中日韩文字、假名、谚文、全角标点与全角 ASCII 等，
     * 这些字符在等宽折算下占 2 个列宽单位；其余（含 ①、☆ 等 Ambiguous 类）按 1 算
     */
    private static boolean isFullWidth(int codePoint) {
        return (codePoint >= 0x1100 && codePoint <= 0x115F)        // 谚文字母
                || (codePoint >= 0x2E80 && codePoint <= 0x303E)    // CJK 部首、符号与标点
                || (codePoint >= 0x3041 && codePoint <= 0x33FF)    // 假名、注音、CJK 兼容字符
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)    // CJK 扩展 A
                || (codePoint >= 0x4E00 && codePoint <= 0x9FFF)    // CJK 统一表意文字
                || (codePoint >= 0xA000 && codePoint <= 0xA4CF)    // 彝文
                || (codePoint >= 0xA960 && codePoint <= 0xA97F)    // 谚文字母扩展 A
                || (codePoint >= 0xAC00 && codePoint <= 0xD7A3)    // 谚文音节
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)    // CJK 兼容表意文字
                || (codePoint >= 0xFE10 && codePoint <= 0xFE19)    // 竖排形式
                || (codePoint >= 0xFE30 && codePoint <= 0xFE6F)    // CJK 兼容形式
                || (codePoint >= 0xFF00 && codePoint <= 0xFF60)    // 全角 ASCII 与全角标点
                || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6)    // 全角符号（￥、￠等）
                || (codePoint >= 0x20000 && codePoint <= 0x3FFFD); // CJK 扩展 B ~ F
    }

}
