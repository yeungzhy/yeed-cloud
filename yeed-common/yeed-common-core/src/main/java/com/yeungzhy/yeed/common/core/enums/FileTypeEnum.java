package com.yeungzhy.yeed.common.core.enums;

import com.yeungzhy.yeed.common.core.support.FileTypeProbeUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文件类型字典：扩展名 <-> MIME 类型
 *
 * <p>消费方有三处：
 * <ul>
 *   <li>oss：上传判真结论落库扩展名与 contentType
 *   <li>gateway：按响应 Content-Type 分类访问日志
 *   <li>job：导出文件的 Content-Type
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-09-04
 * @see FileTypeProbeUtil 文件类型判定
 */
@Getter
@AllArgsConstructor
public enum FileTypeEnum {

    // ---------- 图片 ----------

    /** PNG 图片 */
    PNG(".png", "image/png", "PNG 图片"),

    /** JPEG 图片（规范扩展名取 {@code .jpg}，MIME 为 {@code image/jpeg}） */
    JPG(".jpg", "image/jpeg", "JPEG 图片"),

    /** GIF 动图 */
    GIF(".gif", "image/gif", "GIF 图片"),

    /** WebP 图片 */
    WEBP(".webp", "image/webp", "WebP 图片"),

    /** BMP 位图 */
    BMP(".bmp", "image/bmp", "BMP 图片"),

    /** TIFF 图像（扫描件、传真常见） */
    TIFF(".tiff", "image/tiff", "TIFF 图片"),

    /** SVG 矢量图（XML 文本，可执行脚本，公网场景须谨慎） */
    SVG(".svg", "image/svg+xml", "SVG 矢量图"),

    // ---------- 文档 ----------

    /** PDF 文档 */
    PDF(".pdf", "application/pdf", "PDF 文档"),

    /** 旧版 Word（OLE2 复合文档） */
    DOC(".doc", "application/msword", "Word 97-2003 文档"),

    /** 旧版 Excel（OLE2 复合文档） */
    XLS(".xls", "application/vnd.ms-excel", "Excel 97-2003 工作簿"),

    /** 旧版 PowerPoint（OLE2 复合文档） */
    PPT(".ppt", "application/vnd.ms-powerpoint", "PowerPoint 97-2003 演示文稿"),

    /** Word 文档（OOXML，zip 容器） */
    DOCX(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word 文档"),

    /** Excel 工作簿（OOXML，zip 容器） */
    XLSX(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel 工作簿"),

    /** PowerPoint 演示文稿（OOXML，zip 容器） */
    PPTX(".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "PowerPoint 演示文稿"),

    // ---------- 压缩包 ----------

    /** ZIP 压缩包 */
    ZIP(".zip", "application/zip", "ZIP 压缩包"),

    /** 7-Zip 压缩包 */
    SEVEN_ZIP(".7z", "application/x-7z-compressed", "7z 压缩包"),

    /** RAR 压缩包 */
    RAR(".rar", "application/x-rar-compressed", "RAR 压缩包"),

    /** TAR 归档包 */
    TAR(".tar", "application/x-tar", "TAR 归档包"),

    /** GZIP 压缩包 */
    GZ(".gz", "application/gzip", "GZIP 压缩包"),

    // ---------- 音视频 ----------

    /** MP3 音频 */
    MP3(".mp3", "audio/mpeg", "MP3 音频"),

    /** WAV 音频 */
    WAV(".wav", "audio/wav", "WAV 音频"),

    /** OGG 音频 */
    OGG(".ogg", "audio/ogg", "OGG 音频"),

    /** MP4 视频 */
    MP4(".mp4", "video/mp4", "MP4 视频"),

    /** MPEG 视频 */
    MPEG(".mpeg", "video/mpeg", "MPEG 视频"),

    /** QuickTime 视频 */
    MOV(".mov", "video/quicktime", "QuickTime 视频"),

    /** WebM 视频 */
    WEBM(".webm", "video/webm", "WebM 视频"),

    // ---------- 字体 ----------

    /** TrueType 字体 */
    TTF(".ttf", "font/ttf", "TrueType 字体"),

    /** OpenType 字体（CFF 轮廓） */
    OTF(".otf", "font/otf", "OpenType 字体"),

    // ---------- 文本 ----------

    /** 纯文本 */
    TXT(".txt", "text/plain", "纯文本"),


    ;

    /** 规范扩展名：小写、含点（如 {@code ".xlsx"}），用于对象 key 与下载文件名；落库值取 {@link FileTypeProbeUtil} 判真结论，不接受入参 */
    private final String extension;

    /** 规范 MIME 类型：用于 HTTP Content-Type 与文件记录的 {@code contentType} 列 */
    private final String mimeType;

    /** 中文描述（日志、字典渲染） */
    private final String desc;

    /** MIME（小写）→ 枚举的反查索引，静态初始化一次 */
    private static final Map<String, FileTypeEnum> MIME_TYPE_MAP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                    e -> e.mimeType.toLowerCase(Locale.ROOT), Function.identity(), (first, second) -> first));

    /**
     * 按 MIME 类型反查枚举（开放域查询语义）
     *
     * <p>MIME 多来自下游响应头、取值开放，未命中是常态而非错误，故返回 {@code null} 由调用方兜底，
     * 与封闭域 {@link EnableStatusEnum#parse} 的 fail-fast 相反；大小写不敏感，参数部分须由调用方先行剥离
     *
     * @param mimeType 裸 MIME 类型，不含 {@code ;charset=...} 等参数
     * @return 命中的文件类型；入参为 null 或未命中时返回 null
     */
    public static FileTypeEnum fromMimeTypeOrNull(String mimeType) {
        return mimeType == null ? null : MIME_TYPE_MAP.get(mimeType.toLowerCase(Locale.ROOT));
    }

}
