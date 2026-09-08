package com.yeungzhy.yeed.common.core.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * 文件通用工具：大小格式化、不可信文件名的解析、文件读写删
 *
 * <p> 与 {@link FileTypeProbeUtil} 的分工：本类只做入参整形与字节搬运，不做类型判真
 * 落库扩展名一律取自判真结果，本类对用户扩展名的处理只有「丢弃」
 *
 * @author yeungzhy
 * @since 2026-09-05
 */
public final class FileUtil {

    private FileUtil() {}

    // ============================================= 常量 =============================================

    /** 文件大小展示单位序列（1024 进制，覆盖 long 上限约 8 EiB） */
    private static final String[] SIZE_UNITS = {"B", "KB", "MB", "GB", "TB", "PB", "EB"};

    /**
     * 文件名非法字符：控制字符（含 CRLF，防响应头与日志注入）+ 格式字符
     *
     * <p> 后者含 {@code U+202E}（RLO），可把 {@code photo‮gpj.exe} 反写显示为 {@code photoexe.jpg}，
     * 是「文件名只做展示」这一收口下仍然成立的钓鱼手法
     */
    private static final Pattern UNSAFE_FILE_NAME_CHARS = Pattern.compile("[\\p{Cntrl}\\p{Cf}]");

    // ============================================= 公共 API =============================================

    /**
     * 将字节数格式化为可读大小（1024 进制，只展示最大的单位）
     *
     * <p> 展示规则：B 恒为整数；[10,100) 保留 1 位小数、[100,1024) 取整，
     * KB/MB/GB/TB/PB/EB 同此规则，低于 10 保留 2 位。示例：{@code 512 → "512 B"}、
     * {@code 1536 → "1.50 KB"}、{@code 104857600 → "100 MB"}
     *
     * <p> 只展示最大单位（而非 "1 MB 24 KB" 逐级展开）是通用惯例（Linux {@code ls -h}、
     * Spring {@code DataSize}、commons-io 等）：字符串长度稳定，便于日志对齐与程序解析
     * 逐级展开只服务于人眼速读字节数，不便于机器消费
     *
     * @param bytes 字节数；非正数统一返回 "0 B"
     * @return 形如 "1.50 KB" 的可读字符串
     */
    public static String formatFileSize(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        double size = bytes;
        int unitIndex = 0;
        while (size >= 1024 && unitIndex < SIZE_UNITS.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        if (unitIndex == 0 || size >= 100) {
            return "%.0f %s".formatted(size, SIZE_UNITS[unitIndex]);
        }
        if (size >= 10) {
            return "%.1f %s".formatted(size, SIZE_UNITS[unitIndex]);
        }
        return "%.2f %s".formatted(size, SIZE_UNITS[unitIndex]);
    }

    /**
     * 解析入库主名：剥离路径 → 删控制/格式字符 → 去首尾空白 → 超限截断 → 剥用户扩展名
     *
     * <p> 原始文件名不参与类型判真、也不参与存储路径，但会进下载响应头与列表页展示，仍属不可信入参：
     * 路径段防穿越语义残留，控制字符防响应头/日志注入，格式字符防 RLO 反写欺诈
     *
     * <p> 末步剥扩展名是落库模型要求：主名字段只存主名，扩展名字段一律取
     * {@link FileTypeProbeUtil} 判真结果，用户扩展名必须丢弃，否则改名即可给下载产物挂任意扩展名
     * 无点、或以点开头（如 {@code .gitignore}）时原样返回，故该步不会产生空值
     *
     * @param rawFileName 原始文件名（不可信入参）
     * @param maxLength   主名最大长度（字符数），超长按此截断；由调用方按自身 DB 列宽与响应头上限给出
     * @return 入库主名（不含扩展名）；清洗后为空返回 {@code null}（由调用方转为「文件名为空」）
     * @throws IllegalArgumentException {@code maxLength} 非正数（截断会得到空串，且调用方只判 null，
     *                                  空主名会让完整名退化成纯扩展名，故直接拒绝而非静默产生）
     */
    public static String resolveMainName(String rawFileName, int maxLength) {
        if (maxLength <= 0) {
            throw new IllegalArgumentException("主名最大长度必须为正数: " + maxLength);
        }
        if (rawFileName == null) {
            return null;
        }
        String name = rawFileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = UNSAFE_FILE_NAME_CHARS.matcher(name).replaceAll("").strip();
        if (name.isEmpty()) {
            return null;
        }
        if (name.length() > maxLength) {
            name = name.substring(0, maxLength);
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * 将相对路径解析到 {@code rootPath} 之下的绝对路径（防御穿越根目录）
     *
     * <p> 相对路径若来自外部输入（如对象存储 key）不可直接 resolve：{@code ..} 与绝对前缀都能跳出根目录，
     * 故先 normalize 再校验前缀落在根目录内
     *
     * @throws IllegalArgumentException 解析后不在 {@code rootPath} 之内
     */
    public static Path resolveAbsolutePath(String rootPath, String relativePath) {
        Path root = Paths.get(rootPath).toAbsolutePath().normalize();
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("非法路径，解析后超出根目录: " + relativePath);
        }
        return path;
    }

    /** 写入字节：父目录不存在则创建，文件已存在则整体覆盖 */
    public static void writeBytes(Path path, byte[] data) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, data);
        } catch (IOException e) {
            throw new IllegalStateException("文件写入失败: " + path, e);
        }
    }

    /** 读取文件全部字节 */
    public static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IllegalStateException("文件读取失败: " + path, e);
        }
    }

    /** 删除文件：不存在视为已删除，不抛异常（供"删存储对象"这类幂等操作调用） */
    public static void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new IllegalStateException("文件删除失败: " + path, e);
        }
    }

}
