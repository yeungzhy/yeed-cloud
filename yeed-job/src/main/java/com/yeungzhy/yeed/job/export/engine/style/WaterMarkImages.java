package com.yeungzhy.yeed.job.export.engine.style;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 水印渲染共享零件：字体与 PNG 编码
 *
 * @author yeungzhy
 * @since 2026-09-07
 */
public final class WaterMarkImages {

    /** 水印字体在 classpath 下的位置，缺失即导出失败（不降级） */
    private static final String FONT_RESOURCE = "/font/MiSans-Regular.ttf";
    /** 图片格式：必须支持 alpha 通道，否则透明度失效，JPEG 不可用 */
    private static final String IMAGE_FORMAT = "png";

    private WaterMarkImages() {
    }

    /**
     * 水印默认字体（MiSans Regular，1 号字号）
     *
     * <p>调用方按需 {@code deriveFont} 出目标字号；字体缺失直接抛异常——
     * 环境缺字体属于部署缺陷，静默降级会让水印悄悄变成方框或系统默认字体。
     *
     * @return 共享字体实例
     */
    public static Font font() {
        return FontHolder.DEFAULT_FONT;
    }

    /**
     * 编码为 PNG 字节
     *
     * @param image 待编码的水印图（须带 alpha 通道）
     * @return PNG 字节
     * @throws IllegalStateException 无 PNG 编码器或编码失败（正常 JDK 不会，属环境异常）
     */
    public static byte[] toPngBytes(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, IMAGE_FORMAT, out)) {
                throw new IllegalStateException("找不到 PNG 编码器，无法生成水印图片");
            }
        } catch (IOException e) {
            throw new IllegalStateException("水印图片编码失败", e);
        }
        return out.toByteArray();
    }

    /**
     * 字体懒加载持有者：TTF 约 8MB，按任务重复读盘不可接受，故只加载一次
     *
     * <p>静态内部类持有者惯用法——类加载时才初始化，且天然线程安全。
     */
    private static final class FontHolder {

        private static final Font DEFAULT_FONT = loadFont();

        private FontHolder() {
        }

        private static Font loadFont() {
            try (InputStream in = WaterMarkImages.class.getResourceAsStream(FONT_RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("classpath 下缺少水印字体: " + FONT_RESOURCE);
                }
                return Font.createFont(Font.TRUETYPE_FONT, in);
            } catch (FontFormatException | IOException e) {
                throw new IllegalStateException("水印字体加载失败: " + FONT_RESOURCE, e);
            }
        }
    }

}
