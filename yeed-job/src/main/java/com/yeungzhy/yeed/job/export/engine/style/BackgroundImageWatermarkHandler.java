package com.yeungzhy.yeed.job.export.engine.style;

import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.handler.context.SheetWriteHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFPictureData;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.util.StringUtils;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 水印写处理器（工作表背景图方案）：把半透明斜排文字水印渲染成整版 PNG 注册为 sheet 背景图，
 * Excel 打开后自动平铺整张表
 *
 * <p> 与 {@link HeaderFooterWatermarkHandler} 按可见时机互补。本方案的边界：
 * 不打印（sheet 背景仅屏显）、会被单元格填充色盖住、单元格保持可编辑（背景图不是图形对象，无需保护）
 *
 * <p> XSSF 与 SXSSF 均可：SXSSF 下经 {@link WatermarkSheets} 解到内部 XSSFSheet 挂载：写出时
 * {@code <picture>} 位于 {@code <sheetData>} 之后的保留区，不会随行数据被替换，故流式导出同样有水印
 *
 * @author yeungzhy
 * @since 2026-09-07
 * @see HeaderFooterWatermarkHandler
 * @see WatermarkSheets
 */
@Slf4j
public class BackgroundImageWatermarkHandler implements SheetWriteHandler {

    /** 倾斜角度（度），负值即常见的左下→右上斜铺；0 = 水平，-45 = 更斜 */
    private static final int ANGLE_DEGREES = -25;
    /** 深灰：在白底与浅灰斑马纹上都有足够对比度，纯灰在斑马纹上会「隐形」 */
    private static final Color WATER_MARK_COLOR = new Color(0x40, 0x40, 0x40);

    /**
     * 平铺块尺寸（像素）：分布密度的唯一决定因素，与文本长度无关（相邻块间距 = 块尺寸）；
     * 调大 = 更稀疏，调小 = 更密
     */
    private static final int TILE_WIDTH_PX = 220;
    private static final int TILE_HEIGHT_PX = 150;
    /** 文本块距块边缘的留白（像素）：块内可用区域 = 块尺寸 - 2×留白，同时是相邻块的透明间隔 */
    private static final int MARGIN_PX = 20;
    /** 长文本换行的行数上限：调大 = 长文本字号可更大，但行数多显琐碎 */
    private static final int MAX_LINES = 3;
    /**
     * 字号下限（占构造字号的比例）：换行 + 缩字号仍装不下的极端长文本不再往下压，
     * 宁可文本块等量越出块边界（均匀轻微重叠）也不缩成蚂蚁字
     */
    private static final float MIN_FONT_RATIO = 0.45F;
    /** 优先断行的分隔符：空格 / 制表符 / 连字符 / 下划线 / 斜杠 / 中点 / 常见中英文标点 */
    private static final String LINE_SEPARATORS = " \t-_/·，、；：,;";
    /** 默认字号（磅） */
    private static final float DEFAULT_FONT_SIZE = 23F;
    /** 默认不透明度 0~1：过低水印形同虚设，过高会压住正文 */
    private static final float DEFAULT_ALPHA = 0.15F;

    private final String waterMarkText;
    private final float fontSize;
    private final float alpha;

    /**
     * 按默认字号与透明度构造
     *
     * @param waterMarkText 水印文本；为 null 或空白时静默跳过绘制（创建人未知时不画空水印）
     */
    public BackgroundImageWatermarkHandler(String waterMarkText) {
        this(waterMarkText, DEFAULT_FONT_SIZE, DEFAULT_ALPHA);
    }

    /**
     * @param waterMarkText 水印文本；为 null 或空白时静默跳过绘制；文本内换行符按空格处理
     * @param fontSize      基础字号（磅）：文本变长时实际字号只减不增，下限见 {@link #MIN_FONT_RATIO}
     * @param alpha         不透明度，取值 (0, 1]
     */
    public BackgroundImageWatermarkHandler(String waterMarkText, float fontSize, float alpha) {
        this.waterMarkText = waterMarkText;
        this.fontSize = fontSize;
        this.alpha = alpha;
    }

    /** Sheet 创建后即写入背景图（sheet 级元素，与单元格内容无关，每张 sheet 回调一次） */
    @Override
    public void afterSheetCreate(SheetWriteHandlerContext context) {
        if (!StringUtils.hasText(waterMarkText)) {
            return;
        }

        XSSFSheet xssfSheet = WatermarkSheets.unwrap(context);
        byte[] pictureData = WaterMarkImages.toPngBytes(renderTile());
        registerAsSheetBackground(xssfSheet, pictureData);
    }

    // ============ 内部辅助方法 ========================

    /**
     * 把整版 PNG 注册为 sheet 背景图：图片入库 → worksheet 部件建 relationship → 挂 {@code <picture>} 元素。
     * {@code getCTWorksheet()} 属 POI 内部 API，但这是 POI 无公开背景 API 前提下的既定做法
     */
    private static void registerAsSheetBackground(XSSFSheet xssfSheet, byte[] pictureData) {
        XSSFWorkbook workbook = xssfSheet.getWorkbook();
        int pictureIndex = workbook.addPicture(pictureData, Workbook.PICTURE_TYPE_PNG);
        XSSFPictureData picture = workbook.getAllPictures().get(pictureIndex);
        String relationId = xssfSheet.addRelation(null, XSSFRelation.IMAGES, picture)
                .getRelationship().getId();
        xssfSheet.getCTWorksheet().addNewPicture().setId(relationId);
    }

    /**
     * 渲染单块平铺图：块内画一个「换行 + 字号自适应」的多行文本块，绕块中心旋转居中。
     * 文本块落在块内留白以内，块与块零重叠、零裁切，平铺周期恒等于块尺寸
     */
    private BufferedImage renderTile() {
        int boxWidth = TILE_WIDTH_PX - MARGIN_PX * 2;
        int boxHeight = TILE_HEIGHT_PX - MARGIN_PX * 2;
        Font baseFont = WaterMarkImages.font().deriveFont(Font.PLAIN, fontSize);

        // 先在 1×1 探针图上量文本尺寸：FontMetrics 必须绑定到具体的 Graphics 才有真实宽度
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D probeGraphics = probe.createGraphics();
        probeGraphics.setFont(baseFont);
        FontMetrics baseMetrics = probeGraphics.getFontMetrics();
        TextLayout layout = fitText(baseMetrics, boxWidth, boxHeight);
        probeGraphics.dispose();

        // 按择优字号重测：断行点与字号无关（宽度和目标行宽等比缩放），行内容可直接沿用
        Font finalFont = baseFont.deriveFont(layout.fontSize());
        BufferedImage measureImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D measureGraphics = measureImage.createGraphics();
        measureGraphics.setFont(finalFont);
        BlockMetrics block = measureBlock(measureGraphics.getFontMetrics(), layout.lines());

        // stringWidth 逐行取整在小字号下会累积约百分之几的偏差，超界且字号未触底时按实测比例再收一次
        float minFontSize = fontSize * MIN_FONT_RATIO;
        if (overflowsBox(block, boxWidth, boxHeight) && layout.fontSize() > minFontSize) {
            double shrink = Math.min(boxWidth / rotatedBounds(block.blockWidth(), block.blockHeight())[0],
                    boxHeight / rotatedBounds(block.blockWidth(), block.blockHeight())[1]);
            finalFont = baseFont.deriveFont(
                    Math.max(minFontSize, (float) (layout.fontSize() * shrink)));
            measureGraphics.setFont(finalFont);
            block = measureBlock(measureGraphics.getFontMetrics(), layout.lines());
        }
        measureGraphics.dispose();

        // 字号触底仍装不下：文本块等量越出块边界（每块一致 → 均匀轻微重叠），提示而非静默
        if (overflowsBox(block, boxWidth, boxHeight)) {
            double[] rotated = rotatedBounds(block.blockWidth(), block.blockHeight());
            log.warn("导出任务添加水印，文本块 {}x{}（旋转外接 {}x{}）超出块内可用区域 {}x{}，相邻块将出现均匀轻微重叠（text='{}'）",
                    block.blockWidth(), block.blockHeight(), (int) rotated[0], (int) rotated[1], boxWidth, boxHeight, waterMarkText);
        }

        BufferedImage tile = new BufferedImage(TILE_WIDTH_PX, TILE_HEIGHT_PX, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = tile.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        graphics.setColor(WATER_MARK_COLOR);
        graphics.setFont(finalFont);

        // 绕块中心旋转：以中心为轴，旋转后文本块视觉上仍居中
        double radians = Math.toRadians(ANGLE_DEGREES);
        double centerX = TILE_WIDTH_PX / 2.0;
        double centerY = TILE_HEIGHT_PX / 2.0;
        graphics.rotate(radians, centerX, centerY);
        // 每行独立水平居中；drawString 的 y 是基线，用「块顶 + 行序×行高 + ascent」折算
        double blockTop = centerY - block.blockHeight() / 2.0;
        for (int i = 0; i < layout.lines().size(); i++) {
            float x = (float) (centerX - block.lineWidths()[i] / 2.0);
            float y = (float) (blockTop + i * block.lineHeight() + block.ascent());
            graphics.drawString(layout.lines().get(i), x, y);
        }
        graphics.dispose();

        return tile;
    }

    /**
     * 排版择优：行数 1~{@link #MAX_LINES} 逐个真实试排，选「约束内最终字号最大」的方案。
     * 只按总宽估行数会误判（混合中英文均值行宽不准、分隔符断行产生悬挂短行），
     * 短文本自然选中单行，长文本自动换到多行小字号
     */
    private TextLayout fitText(FontMetrics metrics, int boxWidth, int boxHeight) {
        String text = waterMarkText.replace('\r', ' ').replace('\n', ' ').trim();
        int textWidth = metrics.stringWidth(text);

        TextLayout best = null;
        for (int lineCount = 1; lineCount <= MAX_LINES; lineCount++) {
            List<String> lines = trimLines(capLines(
                    wrapByWidth(text, metrics, textWidth / (float) lineCount), MAX_LINES));
            if (lines.isEmpty()) {
                continue;
            }
            float candidateFontSize = resolveFontSize(metrics, lines, boxWidth, boxHeight);
            if (best == null || candidateFontSize > best.fontSize()) {
                best = new TextLayout(lines, candidateFontSize);
            }
        }
        return best;
    }

    /**
     * 按目标行宽贪心断行：超宽即断，断点优先取行内最后一个分隔符之后（照顾带空格 /
     * 连字符的文本），无分隔符则硬切（中文无词边界概念）。分隔符保留在上一行行尾，
     * 故 {@link #capLines} 合并时直接拼接即可还原原文
     *
     * @return 原始行列表（未 trim，可能带行尾分隔符），至少 1 行
     */
    private static List<String> wrapByWidth(String text, FontMetrics metrics, float targetLineWidth) {
        List<String> lines = new ArrayList<>();
        int lineStart = 0;
        int lastSeparatorEnd = -1;
        float lineWidth = 0F;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            lineWidth += metrics.charWidth(current);
            if (LINE_SEPARATORS.indexOf(current) >= 0) {
                lastSeparatorEnd = i + 1;
            }
            if (lineWidth > targetLineWidth && i < text.length() - 1) {
                int breakAt = lastSeparatorEnd > lineStart ? lastSeparatorEnd : i + 1;
                lines.add(text.substring(lineStart, breakAt));
                lineStart = breakAt;
                i = breakAt - 1;
                lineWidth = 0F;
                lastSeparatorEnd = -1;
            }
        }
        if (lineStart < text.length()) {
            lines.add(text.substring(lineStart));
        }
        return lines;
    }

    /** 行数超上限时把末行并入上一行（断行点分隔符在上一行行尾，直接拼接即还原原文） */
    private static List<String> capLines(List<String> lines, int maxLines) {
        while (lines.size() > maxLines) {
            String last = lines.removeLast();
            lines.set(lines.size() - 1, lines.getLast() + last);
        }
        return lines;
    }

    /** 去掉各行首尾空白；全空白行（连续分隔符导致）丢弃 */
    private static List<String> trimLines(List<String> rawLines) {
        List<String> lines = new ArrayList<>(rawLines.size());
        for (String rawLine : rawLines) {
            String line = rawLine.trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * 求给定行内容可达的最大字号：约束「文本块旋转后的外接矩形 ≤ 块内可用区域」，
     * 块宽高随字号线性缩放故直接比例求交；极端长文本受 {@link #MIN_FONT_RATIO} 托底
     *
     * @return 最终字号；小于 1 视为文本异常，兜底为 1
     */
    private float resolveFontSize(FontMetrics metrics, List<String> lines, int boxWidth, int boxHeight) {
        int blockWidth = 0;
        for (String line : lines) {
            blockWidth = Math.max(blockWidth, metrics.stringWidth(line));
        }
        int blockHeight = metrics.getHeight() * lines.size();

        double[] rotated = rotatedBounds(blockWidth, blockHeight);
        double scale = Math.min(1F, Math.min(boxWidth / rotated[0], boxHeight / rotated[1]));
        float fittedFontSize = (float) (fontSize * scale);
        float minFontSize = fontSize * MIN_FONT_RATIO;
        return Math.max(1F, Math.max(fittedFontSize, minFontSize));
    }

    /** 按当前字号实测文本块：每行宽、块宽（最宽行）、块高（行数×行高）、行高与 ascent */
    private static BlockMetrics measureBlock(FontMetrics metrics, List<String> lines) {
        int[] lineWidths = new int[lines.size()];
        int blockWidth = 0;
        for (int i = 0; i < lines.size(); i++) {
            lineWidths[i] = metrics.stringWidth(lines.get(i));
            blockWidth = Math.max(blockWidth, lineWidths[i]);
        }
        return new BlockMetrics(lineWidths, blockWidth,
                metrics.getHeight() * lines.size(), metrics.getHeight(), metrics.getAscent());
    }

    /** 文本块（轴对齐 w×h）绕中心旋转 {@link #ANGLE_DEGREES} 后的轴对齐外接宽高 */
    private static double[] rotatedBounds(double width, double height) {
        double radians = Math.toRadians(ANGLE_DEGREES);
        return new double[]{
                Math.abs(width * Math.cos(radians)) + Math.abs(height * Math.sin(radians)),
                Math.abs(width * Math.sin(radians)) + Math.abs(height * Math.cos(radians))
        };
    }

    /** 旋转外接矩形是否超出块内可用区域（0.5px 容差吸收取整噪声） */
    private static boolean overflowsBox(BlockMetrics block, int boxWidth, int boxHeight) {
        double[] rotated = rotatedBounds(block.blockWidth(), block.blockHeight());
        return rotated[0] > boxWidth + 0.5 || rotated[1] > boxHeight + 0.5;
    }

    /** 一版排版结果：行内容 + 该方案可达的最大字号 */
    private record TextLayout(List<String> lines,
                              float fontSize) { }

    /** 按某个字号实测出的文本块尺寸：各行宽、块宽（最宽行）、块高（行数×行高）、行高、ascent */
    private record BlockMetrics(int[] lineWidths,
                                int blockWidth,
                                int blockHeight,
                                int lineHeight,
                                int ascent) { }

}
