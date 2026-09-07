package com.yeungzhy.yeed.job.export.engine.style;

import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.handler.context.SheetWriteHandlerContext;
import org.apache.poi.ooxml.POIXMLDocumentPart;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFPictureData;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTLegacyDrawing;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTWorksheet;
import org.springframework.util.StringUtils;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Excel 水印写处理器（页眉页脚方案）：把整版半透明水印图设为页眉图片，打印 / 打印预览时每页都带水印
 *
 * <p>与 {@link BackgroundImageWatermarkHandler} 按可见时机互补：sheet 背景只在屏显可见、打印不出来，
 * 页眉只在页面布局视图 / 打印预览 / 打印输出可见，普通编辑视图看不到。两者同时注册才能「屏幕和打印都有水印」。
 *
 * <p>页眉图片依赖三段配合，缺一不可（POI 5.4.0 实证）：
 * <ul>
 *   <li>页眉文本 {@code &G}：Excel 的图形占位符，{@code setCenter("&G")} 序列化为 {@code &C&G}；</li>
 *   <li>sheet 挂 {@code <legacyDrawingHF r:id/>} 指向 VML 部件——页眉页脚专用，
 *       与批注用的 {@code <legacyDrawing>} 不是同一个元素；</li>
 *   <li>VML 内 {@code <v:shape>} 的 {@code <v:imagedata o:relid/>} 指向图片。注意图片关系必须建在
 *       <b>VML 部件</b>上：{@code o:relid} 以 VML 为基准解析，挂到 sheet 上取不到图（与背景图方案相反）。</li>
 * </ul>
 * POI 无页眉 VML API（{@code XSSFVMLDrawing} 只支持批注图形），故自建 {@link HeaderPictureVml} 覆写
 * {@code commit} 直接写 VML 文本。
 *
 * <p>图尺寸按 {@link PrintSetup#getPaperSize()} 查实际纸张、给整张纸：VML 的
 * {@code mso-position-*-relative:margin} 锚点是打印区中线（不含页边距），图小于纸宽时无从裁切对齐表头。
 *
 * <p>XSSF 与 SXSSF 均可：SXSSF 下经 {@link WatermarkSheets} 解到内部 XSSFSheet 挂载——
 * {@code <headerFooter>} 与 {@code <legacyDrawingHF>} 位于 {@code <sheetData>} 之后的保留区、
 * VML 与图片是独立部件，都不随行数据被替换，故流式导出同样有水印。
 *
 * @author yeungzhy
 * @since 2026-09-07
 * @see BackgroundImageWatermarkHandler
 * @see WatermarkSheets
 */
public class HeaderFooterWatermarkHandler implements SheetWriteHandler {

    /** Excel 页眉页脚的图片占位符：出现在页眉文本里即渲染 legacyDrawingHF 中对应的图形 */
    private static final String HEADER_PICTURE = "&G";
    /** VML 图形 id：Excel 用 id 认槽位，{@code LH/CH/RH} 是左 / 中 / 右页眉、{@code LF/CF/RF} 是页脚 */
    private static final String SHAPE_ID = "CH";
    /** 图形标题（Excel 显示在「设置图片格式」里，仅作标识，不用水印文本以免引入 XML 转义问题） */
    private static final String SHAPE_TITLE = "watermark";

    /**
     * 查询纸张物理尺寸（磅）
     *
     * <p>必须查表而不能写死 A4：{@code pageSetup} 留空时 Excel 按打印机默认纸（常为信纸 612×792）出图，
     * 图小于纸宽时 VML 居中锚后左右留白、无法靠裁切对齐表头。
     *
     * @param paperSize {@link PrintSetup#getPaperSize()} 的纸张代码
     * @param landscape 是否横向打印
     * @return [宽, 高]，单位磅；未识别的纸张回退 A4
     */
    private static float[] paperSize(short paperSize, boolean landscape) {
        // 查表得「纵向」尺寸，landscape 时交换宽高
        float[] portrait = switch (paperSize) {
            case PrintSetup.LETTER_PAPERSIZE -> new float[]{612F, 792F};
            case PrintSetup.LEGAL_PAPERSIZE -> new float[]{612F, 1008F};
            case PrintSetup.A3_PAPERSIZE -> new float[]{842F, 1190F};
            case PrintSetup.A5_PAPERSIZE -> new float[]{420F, 595F};
            case PrintSetup.B4_PAPERSIZE -> new float[]{728F, 1036F};
            case PrintSetup.B5_PAPERSIZE -> new float[]{516F, 728F};
            default -> new float[]{595F, 842F};
        };
        return landscape ? new float[]{portrait[1], portrait[0]} : portrait;
    }
    /** 渲染分辨率：96dpi 下 1pt = 4/3 px，要更锐利的打印件可调到 8/3 */
    private static final float PIXELS_PER_POINT = 4F / 3F;
    /** 版面网格列数：水平密度唯一旋钮，调大 = 更密，长文本时列数过大字号会缩到不可读 */
    private static final int GRID_COLUMNS = 5;
    /**
     * 垂直空白比 = 带间空白 / 水印高度，观感「密还是稀」的决定性指标；水印字高占比恒 = 1/(1+本值)
     *
     * <p>行数不写死，由「字高 × (1 + 本比值)」自适应推出——否则字号被水平约束压小后垂直周期不变，
     * 凭空多出空白。默认 1.08 与背景图方案观感对齐；调小 = 更密，调大 = 更疏。
     */
    private static final float VERTICAL_GAP_RATIO = 1.08F;
    /** 单格内文字可用比例：留边防止相邻格水印贴边 */
    private static final float CELL_FILL_RATIO = 0.9F;
    /** 倾斜角度（度），负值即常见的左下→右上斜铺 */
    private static final int ANGLE_DEGREES = -25;
    /** 深灰：在白底与浅灰斑马纹上都有足够对比度，纯灰在斑马纹上会「隐形」 */
    private static final Color WATER_MARK_COLOR = new Color(0x40, 0x40, 0x40);

    /** 默认字号：单行不换行，文本变长时只缩不增，见 {@link #fitFontByWidth} */
    private static final float DEFAULT_FONT_SIZE = 36F;
    /** 默认不透明度 0~1：过低水印形同虚设，过高会压住正文 */
    private static final float DEFAULT_ALPHA = 0.15F;

    /**
     * 页眉图形模板：%1$s 图形 id、%2$s/%3$s 宽高（磅）、%4$s 图片关系 id、%5$s 标题
     *
     * <p>{@code mso-width-percent:0;mso-height-percent:0} 表示按 style 里的绝对尺寸渲染，
     * 缺省会被 Excel 按百分比缩放；{@code o:relid} 是 VML 引用图片关系的固定写法。
     */
    private static final String VML_TEMPLATE = """
            <xml xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel">
              <o:shapelayout v:ext="edit"><o:idmap v:ext="edit" data="1"/></o:shapelayout>
              <v:shapetype id="_x0000_t202" coordsize="21600,21600" o:spt="202" path="m,l,21600r21600,l21600,xe">
                <v:stroke joinstyle="miter"/>
                <v:path gradientshapeok="t" o:connecttype="rect"/>
              </v:shapetype>
              <v:shape id="%1$s" o:spid="_x0000_s1025" type="#_x0000_t202" style="position:absolute;margin-left:0;margin-top:0;width:%2$.0fpt;height:%3$.0fpt;z-index:-251658240;mso-position-horizontal:center;mso-position-horizontal-relative:margin;mso-position-vertical:center;mso-position-vertical-relative:margin;v-text-anchor:middle" filled="f" stroked="f">
                <v:fill color2="[paint]" o:detectmouseclick="t"/>
                <v:imagedata o:relid="%4$s" o:title="%5$s"/>
                <o:lock v:ext="edit" rotation="t" text="t" shapetype="t"/>
                <v:textbox style="mso-direction-alt:auto"><div style="text-align:center"/></v:textbox>
                <x:ClientData ObjectType="Pict"><x:NoMove/><x:NoResize/><x:Visible/></x:ClientData>
              </v:shape>
            </xml>""";

    private final String waterMarkText;
    private final float fontSize;
    private final float alpha;

    /**
     * 按默认字号与透明度构造
     *
     * @param waterMarkText 水印文本；为 null 或空白时静默跳过绘制（创建人未知时不画空水印）
     */
    public HeaderFooterWatermarkHandler(String waterMarkText) {
        this(waterMarkText, DEFAULT_FONT_SIZE, DEFAULT_ALPHA);
    }

    /**
     * @param waterMarkText 水印文本；为 null 或空白时静默跳过绘制；文本内换行符按空格处理
     * @param fontSize      基础字号：单行排版，文本变长时自动缩小到能装进网格单格（只减不增）
     * @param alpha         不透明度，取值 (0, 1]
     */
    public HeaderFooterWatermarkHandler(String waterMarkText, float fontSize, float alpha) {
        this.waterMarkText = waterMarkText;
        this.fontSize = fontSize;
        this.alpha = alpha;
    }

    /** Sheet 创建后即挂页眉图片（页眉是 sheet 级元素，与单元格内容无关，每张 sheet 回调一次） */
    @Override
    public void afterSheetCreate(SheetWriteHandlerContext context) {
        if (!StringUtils.hasText(waterMarkText)) {
            return;
        }
        XSSFSheet xssfSheet = WatermarkSheets.unwrap(context);
        /*
         * 图 = 整张纸：VML 居中锚到打印区中线后，超出页边距的部分被自然裁切，
         * 剩余可见区两端精确对齐表头两端
         */
        PrintSetup printSetup = xssfSheet.getPrintSetup();
        float[] size = paperSize(printSetup.getPaperSize(), printSetup.getLandscape());
        byte[] pictureData = WaterMarkImages.toPngBytes(renderPage(size[0], size[1]));
        registerHeaderPicture(xssfSheet, pictureData, size[0], size[1]);
    }

    // ============ 内部辅助方法 ========================

    /**
     * 装配「页眉 &G → legacyDrawingHF → VML → 图片」链；每个 sheet 独立一套，
     * 多 sheet 共用 VML 部件会导致 Excel 打开报错
     */
    private static void registerHeaderPicture(XSSFSheet xssfSheet, byte[] pictureData,
                                              float widthPt, float heightPt) {
        XSSFWorkbook workbook = xssfSheet.getWorkbook();
        int pictureIndex = workbook.addPicture(pictureData, Workbook.PICTURE_TYPE_PNG);
        XSSFPictureData picture = workbook.getAllPictures().get(pictureIndex);
        xssfSheet.getHeader().setCenter(HEADER_PICTURE);

        HeaderPictureVml vml = createVmlPart(workbook);
        // 图片关系建在 VML 部件上：o:relid 以 VML 为基准解析，挂到 sheet 上取不到图
        String pictureRelId = vml.addRelation(null, XSSFRelation.IMAGES, picture)
                .getRelationship().getId();
        vml.content = VML_TEMPLATE.formatted(SHAPE_ID, widthPt, heightPt, pictureRelId, SHAPE_TITLE);

        String vmlRelId = xssfSheet.addRelation(null, XSSFRelation.VML_DRAWINGS, vml)
                .getRelationship().getId();
        CTWorksheet ctWorksheet = xssfSheet.getCTWorksheet();
        CTLegacyDrawing legacyDrawing = ctWorksheet.isSetLegacyDrawingHF()
                ? ctWorksheet.getLegacyDrawingHF()
                : ctWorksheet.addNewLegacyDrawingHF();
        legacyDrawing.setId(vmlRelId);
    }

    /** 在 {@code /xl/drawings/} 下新建空白 VML 部件，部件号取 OPC 当前未占用的下标 */
    private static HeaderPictureVml createVmlPart(XSSFWorkbook workbook) {
        OPCPackage opcPackage = workbook.getPackage();
        try {
            int index = opcPackage.getUnusedPartIndex(XSSFRelation.VML_DRAWINGS.getDefaultFileName());
            PackagePartName partName = PackagingURIHelper.createPartName(
                    XSSFRelation.VML_DRAWINGS.getFileName(index));
            PackagePart part = opcPackage.createPart(partName, XSSFRelation.VML_DRAWINGS.getContentType());
            return new HeaderPictureVml(part);
        } catch (InvalidFormatException e) {
            throw new IllegalStateException("页眉水印 VML 部件创建失败", e);
        }
    }

    /** 渲染整版水印图：网格内逐格斜排文字，ARGB 保留 alpha 通道供 Excel 透明渲染 */
    private BufferedImage renderPage(float widthPt, float heightPt) {
        int widthPx = Math.round(widthPt * PIXELS_PER_POINT);
        int heightPx = Math.round(heightPt * PIXELS_PER_POINT);
        int cellWidth = widthPx / GRID_COLUMNS;
        String text = waterMarkText.replace('\r', ' ').replace('\n', ' ').trim();

        // FontMetrics 必须绑定到具体 Graphics 才准，先在 1×1 探针图上定字号并量出真实尺寸
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D probeGraphics = probe.createGraphics();
        Font font = fitFontByWidth(probeGraphics, cellWidth, text);
        probeGraphics.setFont(font);
        FontMetrics metrics = probeGraphics.getFontMetrics();
        int textWidth = metrics.stringWidth(text);
        int lineHeight = metrics.getHeight();
        int ascent = metrics.getAscent();
        probeGraphics.dispose();

        // 行距按「字高 × (1 + 空白比)」自适应推，字号被水平约束压小时行距同步收紧
        double markHeight = rotatedBounds(textWidth, lineHeight)[1];
        int rowPitch = Math.max(1, Math.round((float) (markHeight * (1 + VERTICAL_GAP_RATIO))));
        int rows = Math.max(1, (int) Math.round(heightPx / (double) rowPitch));

        BufferedImage page = new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = page.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        graphics.setColor(WATER_MARK_COLOR);
        graphics.setFont(font);

        double radians = Math.toRadians(ANGLE_DEGREES);
        for (int row = 0; row <= rows; row++) {
            for (int column = 0; column <= GRID_COLUMNS; column++) {
                /*
                 * 边缘水印不骑 x=0/y=0：骑边缘会把字左半截画到图外被裁（「最左一列丢字」），
                 * 收进图内 1/4 格后水印左缘仍贴纸张左缘、文字完整
                 */
                int centerX = column * cellWidth + cellWidth / 4;
                int centerY = row * rowPitch + rowPitch / 4;
                graphics.translate(centerX, centerY);
                graphics.rotate(radians);
                // drawString 的 y 是文字基线，用「行中线 - 半行高 + ascent」折算
                graphics.drawString(text, -textWidth / 2F, (rowPitch - lineHeight) / 2F + ascent);
                graphics.rotate(-radians);
                graphics.translate(-centerX, -centerY);
            }
        }
        graphics.dispose();

        return page;
    }

    /**
     * 按列宽求可达字号（约束「旋转后外接宽度 ≤ 列宽 × {@link #CELL_FILL_RATIO}」，短文本不放大）。
     * 只约束水平：垂直行距由 {@link #VERTICAL_GAP_RATIO} 自适应，若按格高压字号会凭空多出空白
     */
    private Font fitFontByWidth(Graphics2D graphics, int cellWidth, String text) {
        Font baseFont = WaterMarkImages.font().deriveFont(Font.PLAIN, fontSize);
        FontMetrics baseMetrics = graphics.getFontMetrics(baseFont);
        double rotatedWidth = rotatedBounds(baseMetrics.stringWidth(text), baseMetrics.getHeight())[0];

        double scale = Math.min(1F, cellWidth * CELL_FILL_RATIO / rotatedWidth);
        return baseFont.deriveFont(Math.max(1F, (float) (fontSize * scale)));
    }

    /** 文本（轴对齐 w×h）绕中心旋转 {@link #ANGLE_DEGREES} 后的轴对齐外接宽高 */
    private static double[] rotatedBounds(double width, double height) {
        double radians = Math.toRadians(ANGLE_DEGREES);
        return new double[]{
                Math.abs(width * Math.cos(radians)) + Math.abs(height * Math.sin(radians)),
                Math.abs(width * Math.sin(radians)) + Math.abs(height * Math.cos(radians))
        };
    }

    /**
     * 页眉图形部件：POI 的 {@code XSSFVMLDrawing} 只支持批注图形，页眉图片只能自写 VML，
     * 借 {@code commit}（POI 序列化部件的既定扩展点）落盘装配好的 VML 文本
     */
    private static final class HeaderPictureVml extends POIXMLDocumentPart {

        /** VML 文本；图片关系 id 要先建出来才知道，故由外层在 commit 前赋值 */
        private String content;

        private HeaderPictureVml(PackagePart part) {
            super(part);
        }

        @Override
        protected void commit() throws IOException {
            try (OutputStream out = getPackagePart().getOutputStream()) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

}
