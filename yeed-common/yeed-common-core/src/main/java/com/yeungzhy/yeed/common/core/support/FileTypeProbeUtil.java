package com.yeungzhy.yeed.common.core.support;

import com.yeungzhy.yeed.common.core.enums.FileTypeEnum;
import com.yeungzhy.yeed.common.core.exception.BizException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 文件真实类型探测工具
 *
 * <p> OSS 等存储服务应在落库前用本工具判真，探测结果决定
 * 存储对象 key 的扩展名与回放的 Content-Type，原始文件名只作为展示字段入库
 *
 * <p> 失败语义：类型不支持 / 无法判定一律抛 {@link BizException}
 *
 * <p> 输入可以是完整文件，也可以是仅含头部的片段（流式上传场景）：判据全部基于前部字节，
 * 且 OOXML 另有不依赖解压的回退判据，故片段输入同样能判出 docx/xlsx/pptx（见 {@link #zipFamily}）
 *
 * <p> 同包 {@link FileUtil} 负责大小格式化、文件名解析与文件读写，本类不涉及
 *
 * @author yeungzhy
 * @since 2026-09-04
 */
public final class FileTypeProbeUtil {

    private FileTypeProbeUtil() {}

    // ============================================= 类型常量 =============================================

    /** 未命中任何判据时的统一失败话术 */
    private static final String UNSUPPORTED_MESSAGE = "无法识别的文件类型";

    /** 文本判定的最大扫描字节数（svg 特征、控制字符统计均在此窗口内） */
    private static final int TEXT_SCAN_BYTES = 512;
    /** OOXML 三分需读取的 [Content_Types].xml 内容上限，防止压缩炸弹撑爆内存 */
    private static final int CONTENT_TYPES_READ_LIMIT = 8192;
    /** ZipInputStream 扫描 [Content_Types].xml 的最大 entry 数：规范未强制其位于首位，但主流生成器均写在前部 */
    private static final int ZIP_ENTRY_SCAN_LIMIT = 64;

    // OOXML 三分依据：[Content_Types].xml 内的 Override PartName 关键字
    private static final String CT_TAG_WORD = "wordprocessingml";
    private static final String CT_TAG_EXCEL = "spreadsheetml";
    private static final String CT_TAG_PPT = "presentationml";

    // OOXML 回退判据：zip local file header 内的 entry 名（明文，无需解压即可搜到）
    private static final String ENTRY_WORD = "word/document.xml";
    private static final String ENTRY_EXCEL = "xl/workbook.xml";
    private static final String ENTRY_PPT = "ppt/presentation.xml";
    /**
     * 回退判据的扫描窗口：只覆盖 zip 前部的 local file header 区
     *
     * <p> 各条目的压缩数据紧跟其 header 之后，故主文档部件靠前时其 header 必落在本窗口内
     * 刻意不放大，这是「搜明文」的兜底手段，扫太大既浪费又可能命中压缩数据里的巧合字节
     */
    private static final int ENTRY_SCAN_BYTES = 65536;

    // ============================================= OLE2 复合文档布局（MS-CFB） =============================================

    /** 头部 30 偏移：扇区大小指数 {@code 1 << n}（uint16 LE，合法值仅 9=512B、12=4096B） */
    private static final int OLE2_SECTOR_SHIFT_OFFSET = 30;
    /** 头部 48 偏移：首个目录扇区号（uint32 LE）；扇区号 n 的文件偏移为 {@code (n + 1) * 扇区大小} */
    private static final int OLE2_DIR_SECTOR_OFFSET = 48;
    /** 目录项定长 128 字节 */
    private static final int OLE2_DIR_ENTRY_SIZE = 128;
    /** 目录项 64 偏移：名称字节数（uint16 LE，含结尾的 UTF-16 终止符） */
    private static final int OLE2_NAME_LENGTH_OFFSET = 64;
    /** 目录项名称区定长 64 字节（UTF-16LE） */
    private static final int OLE2_NAME_MAX_BYTES = 64;
    /** 首个目录扇区内扫描的目录项数上限（512B 扇区仅 4 项、4096B 扇区 32 项；主流文档的主数据流在前几项） */
    private static final int OLE2_DIR_ENTRY_SCAN_LIMIT = 32;

    // doc/xls/ppt 三分依据：复合文档目录项里的主数据流名（与 POI / Tika 的判定口径一致）
    private static final String OLE2_STREAM_WORD = "WordDocument";
    private static final String OLE2_STREAM_EXCEL = "Workbook";
    private static final String OLE2_STREAM_EXCEL_LEGACY = "Book";
    private static final String OLE2_STREAM_PPT = "PowerPoint Document";
    /** 加密文档（含加密的 docx/xlsx/pptx）外壳同为 CFB，主数据流名为此值，须单独拒发以给出准确话术 */
    private static final String OLE2_STREAM_ENCRYPTED = "EncryptedPackage";

    // ============================================= 公共 API =============================================

    /**
     * 探测文件真实类型，返回可安全落库的类型
     *
     * @param data 文件二进制内容，须非空（空抛 BizException）
     * @return 判真得到的文件类型；其扩展名与 MIME 取自同一枚举常量，不存在二者不一致的可能
     * @throws BizException 内容为空 / 类型不支持或无法判定
     */
    public static FileTypeEnum probe(byte[] data) {
        if (data == null || data.length == 0) {
            throw new BizException("上传文件内容为空");
        }
        FileTypeEnum result = matchByMagic(data);
        if (result != null) {
            return result;
        }
        throw new BizException(UNSUPPORTED_MESSAGE);
    }

    // ============================================= 私有方法：魔数判定链 =============================================

    /**
     * 按文件头字节顺序比对魔数，返回命中类型；未命中（含非文本内容）返回 null
     *
     * <p> 判据优先级注意点：
     * <ul>
     *   <li>docx/xlsx/pptx 是 zip 容器，必须置于普通 zip 之前（zip 分支内做 OOXML 三分）
     *   <li>doc/xls/ppt 的 OLE2 魔数独立于 zip，置于 zip 前（OLE2 不是 zip 容器）
     *   <li>webp/wav 同为 RIFF 容器，按 +8 偏移的格式标识区分
     *   <li>mp3 帧同步（{@code 0xFF 0xEx}）属弱魔数，置于强魔数之后避免误伤
     * </ul>
     */
    private static FileTypeEnum matchByMagic(byte[] data) {
        if (match(data, 0, 0x89, 0x50, 0x4E, 0x47)) {                       // PNG 头
            return FileTypeEnum.PNG;
        }
        if (match(data, 0, 0xFF, 0xD8, 0xFF)) {                             // JPEG 头
            return FileTypeEnum.JPG;
        }
        if (match(data, 0, 0x47, 0x49, 0x46, 0x38)                          // "GIF8"
                && data.length > 5
                && (data[4] == 0x37 || data[4] == 0x39)                      // "87a"/"89a" 的版本位
                && data[5] == 0x61) {
            return FileTypeEnum.GIF;
        }
        if (match(data, 0, 0x42, 0x4D)) {                                   // "BM"
            return FileTypeEnum.BMP;
        }
        if ((match(data, 0, 0x49, 0x49, 0x2A, 0x00))                        // TIFF: 小端 "II*\0"
                || match(data, 0, 0x4D, 0x4D, 0x00, 0x2A)) {                //      大端 "MM\0*"
            return FileTypeEnum.TIFF;
        }
        if (match(data, 0, 0x52, 0x49, 0x46, 0x46) && match(data, 8, 0x57, 0x45, 0x42, 0x50)) { // RIFF....WEBP
            return FileTypeEnum.WEBP;
        }
        if (match(data, 0, 0x52, 0x49, 0x46, 0x46) && match(data, 8, 0x57, 0x41, 0x56, 0x45)) { // RIFF....WAVE
            return FileTypeEnum.WAV;
        }
        if (match(data, 0, 0x25, 0x50, 0x44, 0x46)) {                       // "%PDF"
            return FileTypeEnum.PDF;
        }
        if (match(data, 0, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) { // OLE2 复合文档（doc/xls/ppt）
            return ole2Family(data);
        }
        if (match(data, 0, 0x50, 0x4B, 0x03, 0x04)) {                       // zip 容器（含 OOXML）
            return zipFamily(data);
        }
        if (match(data, 0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C)) {           // 7z
            return FileTypeEnum.SEVEN_ZIP;
        }
        if (match(data, 0, 0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)) {     // "Rar!\x1A\x07\0"
            return FileTypeEnum.RAR;
        }
        if (match(data, 0, 0x1F, 0x8B)) {                                   // gzip
            return FileTypeEnum.GZ;
        }
        if (match(data, 257, 0x75, 0x73, 0x74, 0x61, 0x72)) {               // tar: "ustar" 位于 257 偏移（POSIX 头）
            return FileTypeEnum.TAR;
        }
        if (match(data, 0, 0x1A, 0x45, 0xDF, 0xA3) && containsAscii(data, 0, 128, "webm")) { // EBML + 文档类型声明
            return FileTypeEnum.WEBM;
        }
        if (match(data, 0, 0x4F, 0x67, 0x67, 0x53)) {                       // "OggS"
            return FileTypeEnum.OGG;
        }
        if (match(data, 0, 0x49, 0x44, 0x33)) {                             // MP3 ID3v2 标签头
            return FileTypeEnum.MP3;
        }
        if (data.length > 1 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xE0) == 0xE0) { // MP3 帧同步 0xFF 0xEx
            return FileTypeEnum.MP3;
        }
        if (match(data, 0, 0x00, 0x00, 0x01, 0xBA)                          // MPEG 节目流（PS）起始码
                || match(data, 0, 0x00, 0x00, 0x01, 0xB3)) {                // MPEG-1 视频序列头
            return FileTypeEnum.MPEG;
        }
        if (match(data, 4, 0x66, 0x74, 0x79, 0x70)) {                       // ISO BMFF box "ftyp"（mp4/mov）
            return isoBmffFamily(data);
        }
        if (match(data, 0, 0x00, 0x01, 0x00, 0x00)) {                       // TrueType
            return FileTypeEnum.TTF;
        }
        if (match(data, 0, 0x4F, 0x54, 0x54, 0x4F)) {                       // "OTTO"（CFF 轮廓 OpenType）
            return FileTypeEnum.OTF;
        }
        // 文本类型无魔数：所有魔数未命中后再走文本启发，避免把二进制误判为文本
        return matchText(data);
    }

    /**
     * OLE2 复合文档（doc/xls/ppt）：三者共用同一魔数，按目录项里的主数据流名三分
     *
     * <p> 目录扇区由头部给出（{@code (扇区号 + 1) * 扇区大小}），扇区内是定长 128B 的目录项，
     * 0 号恒为 Root Entry，故从 1 号起扫描。主数据流由生成器首个写入，必在同一个扇区内
     *
     * @throws BizException 头部结构异常（扇区大小非法 / 目录扇区越界）、是加密文档、或无任何已知主数据流
     */
    private static FileTypeEnum ole2Family(byte[] data) {
        int sectorShift = readUint16(data, OLE2_SECTOR_SHIFT_OFFSET);
        if (sectorShift != 9 && sectorShift != 12) {
            throw new BizException(UNSUPPORTED_MESSAGE);
        }
        int sectorSize = 1 << sectorShift;
        long dirOffset = (readUint32(data, OLE2_DIR_SECTOR_OFFSET) + 1) * sectorSize;
        if (dirOffset < 0 || dirOffset + sectorSize > data.length) {
            throw new BizException(UNSUPPORTED_MESSAGE);
        }
        int entryCount = Math.min(sectorSize / OLE2_DIR_ENTRY_SIZE, OLE2_DIR_ENTRY_SCAN_LIMIT);
        for (int i = 1; i < entryCount; i++) {
            String streamName = readDirectoryEntryName(data, (int) dirOffset + i * OLE2_DIR_ENTRY_SIZE);
            switch (streamName) {
                case OLE2_STREAM_ENCRYPTED -> throw new BizException("不支持加密的 Office 文档，请先解除文档密码后上传");
                case OLE2_STREAM_WORD -> {
                    return FileTypeEnum.DOC;
                }
                case OLE2_STREAM_EXCEL, OLE2_STREAM_EXCEL_LEGACY -> {
                    return FileTypeEnum.XLS;
                }
                case OLE2_STREAM_PPT -> {
                    return FileTypeEnum.PPT;
                }
            }
        }
        throw new BizException("无法识别的旧版 Office 文档（doc/xls/ppt），请转换为新版 docx/xlsx/pptx 后上传");
    }

    /**
     * zip 家族：先按 {@code [Content_Types].xml} 关键字做 OOXML 三分（docx/xlsx/pptx），
     * 未命中再退到「搜 zip 条目名明文」，仍无结果才按普通 zip 处理
     *
     * <p> 为何需要回退：{@link #readContentTypesXml} 用 {@code ZipInputStream} 从首字节顺序解压，
     * 任一 entry 读不完即 EOF → 返回 null → 误判为 zip。实测两类真实输入会踩到：
     * <ul>
     *   <li>流式上传只给头部（如 16KB），zip 尾部缺失导致顺序解压中断
     *   <li>POI SXSSF 导出的 xlsx 条目顺序与普通生成器不同，前 64 个 entry 内可能读不到
     *       {@code [Content_Types].xml}
     * </ul>
     *
     * <p> 回退判据不解压：zip 的 local file header 里条目名是明文，直接在前部窗口搜主文档部件名即可三分
     * 权威判据仍在前，回退仅用于兜底
     */
    private static FileTypeEnum zipFamily(byte[] data) {
        String contentTypeXml = readContentTypesXml(data);
        if (contentTypeXml != null) {
            if (contentTypeXml.contains(CT_TAG_EXCEL)) {
                return FileTypeEnum.XLSX;
            }
            if (contentTypeXml.contains(CT_TAG_WORD)) {
                return FileTypeEnum.DOCX;
            }
            if (contentTypeXml.contains(CT_TAG_PPT)) {
                return FileTypeEnum.PPTX;
            }
        }
        // 回退：只按「主文档部件名」三分，不尝试还原 [Content_Types].xml
        if (containsAscii(data, 0, ENTRY_SCAN_BYTES, ENTRY_EXCEL)) {
            return FileTypeEnum.XLSX;
        }
        if (containsAscii(data, 0, ENTRY_SCAN_BYTES, ENTRY_WORD)) {
            return FileTypeEnum.DOCX;
        }
        if (containsAscii(data, 0, ENTRY_SCAN_BYTES, ENTRY_PPT)) {
            return FileTypeEnum.PPTX;
        }
        return FileTypeEnum.ZIP;
    }

    /** 从 zip 容器读取根目录 {@code [Content_Types].xml} 的文本内容；不存在或读取超限返回 null */
    private static String readContentTypesXml(byte[] data) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            for (int i = 0; i < ZIP_ENTRY_SCAN_LIMIT; i++) {
                ZipEntry entry = zis.getNextEntry();
                if (entry == null) {
                    return null;
                }
                if (entry.isDirectory() || !"[Content_Types].xml".equals(entry.getName())) {
                    continue;
                }
                int total = 0;
                byte[] buffer = new byte[CONTENT_TYPES_READ_LIMIT];
                while (total < buffer.length) {
                    int read = zis.read(buffer, total, buffer.length - total);
                    if (read < 0) {
                        break;
                    }
                    total += read;
                }
                return new String(buffer, 0, total, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // 半截 zip / 伪装容器：交给调用方按普通 zip 处理，此处不抛出
            return null;
        }
        return null;
    }

    /**
     * ISO BMFF（mp4/mov）：容器同构，按 {@code ftyp} 的 major_brand 区分
     *
     * <p> Apple QuickTime 系列（{@code qt  }）为 mov，其余（isom/mp42/avc1 等）为 mp4
     */
    private static FileTypeEnum isoBmffFamily(byte[] data) {
        boolean quickTime = data.length >= 12
                && data[8] == 'q' && data[9] == 't' && data[10] == ' ' && data[11] == ' ';
        return quickTime ? FileTypeEnum.MOV : FileTypeEnum.MP4;
    }

    /**
     * 文本类型（svg/txt）：无魔数，仅当内容经启发确为文本时才进入
     *
     * <p> svg 需含 {@code <svg} 特征（其 MIME 与纯文本不同，必须区分）；其余文本一律归为
     * {@link FileTypeEnum#TXT}，md 无字节特征可区分且 MIME 同为 {@code text/plain}
     */
    private static FileTypeEnum matchText(byte[] data) {
        if (!looksLikeText(data)) {
            return null;
        }
        if (containsAscii(data, 0, TEXT_SCAN_BYTES, "<svg")) {
            return FileTypeEnum.SVG;
        }
        return FileTypeEnum.TXT;
    }

    // ============================================= 私有方法：字节读取辅助 =============================================

    /** 读取小端 uint16；数据不足返回 -1（调用方按非法值处理） */
    private static int readUint16(byte[] data, int offset) {
        if (offset < 0 || offset + 2 > data.length) {
            return -1;
        }
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    /** 读取小端 uint32（以 long 承载，避免高位为 1 时成负数）；数据不足返回 -1 */
    private static long readUint32(byte[] data, int offset) {
        if (offset < 0 || offset + 4 > data.length) {
            return -1L;
        }
        return (long) (data[offset] & 0xFF)
                | ((long) (data[offset + 1] & 0xFF) << 8)
                | ((long) (data[offset + 2] & 0xFF) << 16)
                | ((long) (data[offset + 3] & 0xFF) << 24);
    }

    /**
     * 读取 OLE2 目录项名称（UTF-16LE，去掉结尾终止符）；名称长度非法返回空串
     *
     * @param base 目录项起始偏移，调用方须保证 {@code base + 128 <= data.length}
     */
    private static String readDirectoryEntryName(byte[] data, int base) {
        int nameBytes = readUint16(data, base + OLE2_NAME_LENGTH_OFFSET);
        if (nameBytes < 2 || nameBytes > OLE2_NAME_MAX_BYTES || (nameBytes & 1) != 0) {
            return "";
        }
        return new String(data, base, nameBytes - 2, StandardCharsets.UTF_16LE);
    }

    /** 从 {@code offset} 起顺序比对魔数 {@code magic}；数据不足直接判否 */
    private static boolean match(byte[] data, int offset, int... magic) {
        if (data.length < offset + magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if ((data[offset + i] & 0xFF) != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /** 在 {@code [from, from + length)} 窗口内查找 ASCII 子串（大小写敏感，用于文本特征定位） */
    private static boolean containsAscii(byte[] data, int from, int length, String text) {
        int end = Math.min(data.length, from + length);
        if (end - from < text.length()) {
            return false;
        }
        for (int i = from; i <= end - text.length(); i++) {
            boolean hit = true;
            for (int j = 0; j < text.length(); j++) {
                if (data[i + j] != (byte) text.charAt(j)) {
                    hit = false;
                    break;
                }
            }
            if (hit) {
                return true;
            }
        }
        return false;
    }

    /**
     * 文本启发：前 {@link #TEXT_SCAN_BYTES} 字节中不可打印控制字符占比低于 3% 视为文本
     *
     * <p> 保留 \t \n \r 换页符，避免正常文本被误判；\0 一律计入控制字符
     * （UTF-16 文本会被判非文本，属有意取舍）
     */
    private static boolean looksLikeText(byte[] data) {
        int limit = Math.min(data.length, TEXT_SCAN_BYTES);
        int control = 0;
        for (int i = 0; i < limit; i++) {
            int b = data[i] & 0xFF;
            if (b == 0x00) {
                control++;
            } else if (b < 0x20 && b != 0x09 && b != 0x0A && b != 0x0C && b != 0x0D) {
                control++;
            } else if (b == 0x7F) {
                control++;
            }
        }
        return control * 100L < limit * 3L;
    }

}
