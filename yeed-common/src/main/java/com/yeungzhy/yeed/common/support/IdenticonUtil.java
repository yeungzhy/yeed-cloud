package com.yeungzhy.yeed.common.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Identicon 头像生成工具（GitHub 默认头像风格）
 *
 * <p>算法原理：{@code SHA-256(seed)} 是确定性纯函数 —— 同一 seed 永远生成同一张图，
 * 不同 seed 视觉差异明显；因此不需要存储生成结果，每次按 seed 实时计算即可。
 *
 * <p>具体规则：
 * <ol>
 *     <li>SHA-256(seed) → 256 位位流</li>
 *     <li>第 1 字节 → HSL 色相（0°-360°），第 2 字节高/低 4 位分别映射为饱和度与亮度
 *         → 颜色饱满但不刺眼；最终转为 #RRGGBB 十六进制色值</li>
 *     <li>剩余位流中取 {@code GRID × ceil(GRID/2)} 位 = {@code GRID} 行 × 左半独立列，
 *         按位决定左半列是否填色；右半由左半水平镜像派生，
 *         形成左右对称的视觉效果</li>
 *     <li>输出 SVG（viewBox="0 0 GRID GRID"，矢量缩放不失真），
 *         前端通过 CSS width/height 控制展示尺寸</li>
 * </ol>
 *
 * <p>GRID 安全上限：颜色固定占用前 16 位，剩 240 位供网格决策；
 * 为保证每个决策位不被循环复用（分布最佳），需满足 {@code GRID × ceil(GRID/2) ≤ 240}，
 * 即 GRID ∈ [3, 21]，默认 7。
 *
 * <p>深色模式：{@code dark=true} 时背景切换为深灰 #1e1e1e，前景亮度区间整体提亮，
 * 确保在深色背景上对比度足够。色相与饱和度两种模式一致，同一用户切换主题时色系保持连贯。
 *
 * @author yeungzhy
 * @since 2026-08-06
 */
public final class IdenticonUtil {

    /* ============================================= 算法常量 ============================================= */

    /** 默认网格尺寸（位数充裕，7×7 视觉丰富且分布均匀） */
    public static final int DEFAULT_GRID = 7;

    /** 浅色模式背景色 */
    private static final String BG_LIGHT = "#ffffff";
    /** 深色模式背景色（深灰，不用纯黑避免过于刺眼） */
    private static final String BG_DARK = "#1e1e1e";

    /** MessageDigest 算法名 */
    private static final String ALGORITHM = "SHA-256";
    /** 颜色固定占用前 16 位（前 2 字节：色相 8bit + 饱和度 4bit + 亮度 4bit） */
    private static final int COLOR_BITS = 16;
    /** 最少 3 列，再小视觉上无法识别为"图案" */
    private static final int MIN_GRID = 3;
    /** 最多 21 列：SHA-256 去掉颜色 16 位后剩 240 位，满足 GRID × ceil(GRID/2) ≤ 240 */
    private static final int MAX_GRID = 21;

    private IdenticonUtil() {}


    /* ============================================= 公共 API ============================================= */

    /**
     * 生成 identicon SVG 字符串（默认网格，支持深色模式）
     *
     * @param seed 种子（唯一即可，推荐传用户 id 的 toString）
     * @param dark 是否深色模式（深色背景 + 提亮前景色）
     * @return 完整 SVG 文档字符串，可直接作为 image/svg+xml 响应返回
     * @throws IllegalArgumentException 种子为 null/空白
     */
    public static String generate(String seed, boolean dark) {
        return generate(seed, DEFAULT_GRID, dark);
    }

    /**
     * 生成 identicon SVG 字符串（完全自定义）
     *
     * @param seed 种子（唯一即可，推荐传用户 id 的 toString）
     * @param grid 网格尺寸，∈ [3, 21]
     * @param dark 是否深色模式（深色背景 + 提亮前景色）
     * @return 完整 SVG 文档字符串，可直接作为 image/svg+xml 响应返回
     * @throws IllegalArgumentException grid 越界或种子为 null/空白
     */
    public static String generate(String seed, int grid, boolean dark) {
        // 1. 参数校验
        if (seed == null || seed.isBlank()) {
            throw new IllegalArgumentException("seed must not be null or blank");
        }
        if (grid < MIN_GRID || grid > MAX_GRID) {
            throw new IllegalArgumentException(String.format(
                    "grid must be in [%d, %d], but was %d", MIN_GRID, MAX_GRID, grid));
        }

        // 2. 哈希
        byte[] hash = digest(seed);

        // 3. 派生颜色 & 网格
        String bgColor = dark ? BG_DARK : BG_LIGHT;
        String fillColor = deriveColor(hash, dark);
        boolean[][] gridArray = deriveGrid(hash, grid, COLOR_BITS);

        // 4. SVG 组装（viewBox 与背景 rect 必须用实际 grid，保证大网格不被裁剪）
        StringBuilder sb = new StringBuilder(512);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
          .append(grid).append(' ').append(grid)
          .append("\" shape-rendering=\"crispEdges\">");
        sb.append("<rect width=\"").append(grid)
          .append("\" height=\"").append(grid)
          .append("\" fill=\"").append(bgColor).append("\"/>");
        for (int r = 0; r < grid; r++) {
            for (int c = 0; c < grid; c++) {
                if (gridArray[r][c]) {
                    sb.append("<rect x=\"").append(c)
                      .append("\" y=\"").append(r)
                      .append("\" width=\"1\" height=\"1\" fill=\"").append(fillColor).append("\"/>");
                }
            }
        }
        sb.append("</svg>");
        return sb.toString();
    }


    /* ============================================= 内部实现 ============================================= */

    /** 计算种子 SHA-256 哈希。SHA-256 为 JDK 标准算法，不可用则抛 ISE。 */
    private static byte[] digest(String seed) {
        try {
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            return md.digest(seed.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ALGORITHM + " algorithm unavailable on this JVM", e);
        }
    }

    /**
     * 从哈希前 2 字节派生 HSL 颜色，并转换为十六进制 #RRGGBB。
     * 色相范围 0-360° 全量，饱和度 65-100%；
     * 亮度区间随 dark 切换：浅色 40-70%（白底可读），深色 55-80%（深底可读）。
     */
    private static String deriveColor(byte[] hash, boolean dark) {
        float hue        = (hash[0] & 0xFF) * 360.0f / 256.0f;
        float saturation = 0.65f + ((hash[1] >> 4) & 0x0F) / 15.0f * 0.35f;   // 65% ~ 100%
        float lightMin   = dark ? 0.55f : 0.40f;
        float lightMax   = dark ? 0.80f : 0.70f;
        float lightness  = lightMin + (hash[1] & 0x0F) / 15.0f * (lightMax - lightMin);
        int[] rgb = hslToRgb(hue, saturation, lightness);
        return String.format("#%02x%02x%02x", rgb[0], rgb[1], rgb[2]);
    }

    /**
     * 从哈希位流派生 GRID × GRID 填色网格。
     *
     * @param hash     哈希字节数组
     * @param grid     网格尺寸（单边）
     * @param skipBits 开头跳过的位数（被颜色消费的位数）
     */
    private static boolean[][] deriveGrid(byte[] hash, int grid, int skipBits) {
        int leftCols = (grid + 1) / 2;
        boolean[][] gridArray = new boolean[grid][grid];
        int bitIndex = 0;
        for (int r = 0; r < grid; r++) {
            for (int lc = 0; lc < leftCols; lc++) {
                boolean filled = getBit(hash, skipBits + bitIndex);
                gridArray[r][lc] = filled;
                // 水平镜像：第 GRID 列 = 第 1 列，第 GRID-1 列 = 第 2 列 ...
                int mirrorCol = (grid - 1) - lc;
                if (mirrorCol != lc) {
                    gridArray[r][mirrorCol] = filled;
                }
                bitIndex++;
            }
        }
        return gridArray;
    }

    /** 从哈希字节数组中取第 n 位（位序：字节内高位在前，MSB first）。 */
    private static boolean getBit(byte[] hash, int n) {
        int byteIdx = n >> 3;        // n / 8
        int bitIdx  = 7 - (n & 0x7); // 字节内从高位(MSB)往低位取
        // 安全兜底：前面 MAX_GRID 校验已保证不会进这个分支；取模避免越界作为防御性设计
        return ((hash[byteIdx % hash.length] >> bitIdx) & 0x01) == 1;
    }

    /**
     * 标准 HSL → RGB 转换（W3C 公式）。
     *
     * @param h 色相 0-360（度）
     * @param s 饱和度 0-1
     * @param l 亮度 0-1
     * @return [R, G, B]，每个分量 0-255
     */
    private static int[] hslToRgb(float h, float s, float l) {
        float r, g, b;
        if (s == 0f) {
            r = g = b = l; // 灰度
        } else {
            float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
            float p = 2 * l - q;
            r = hueToRgb(p, q, h / 360f + 1f / 3f);
            g = hueToRgb(p, q, h / 360f);
            b = hueToRgb(p, q, h / 360f - 1f / 3f);
        }
        return new int[]{
                Math.round(r * 255),
                Math.round(g * 255),
                Math.round(b * 255)
        };
    }

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1f / 6f) return p + (q - p) * 6 * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6;
        return p;
    }

}
