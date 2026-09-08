package com.yeungzhy.yeed.oss.sys.file.config;

import com.yeungzhy.yeed.oss.sys.file.enums.OssStorageTypeEnum;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * OSS 文件存储配置项
 *
 * <p>配置示例（application.yaml / Nacos yeed-oss.yaml 覆盖）：
 * {@snippet lang="yaml":
 * yeed-oss:
 *   oss:
 *     max-upload-size: 30   # 裸数字按 @DataSizeUnit 取 MB；写 512KB / 2GB 亦可，显式单位优先
 *     expire-after: 90      # 裸数字按 @DurationUnit 取天；写 12h / PT12H 亦可，显式单位优先
 *     local:
 *       storage-path: ./file/oss
 * }
 *
 * <p>时长与大小一律用 {@link Duration} / {@link DataSize} 承载：绑定层内建单位解析，
 * 调用方拿到的是已换算好的值，不必再约定"这个字段的单位是毫秒还是字节"
 *
 * @author yeungzhy
 * @since 2026-08-23
 */
@Data
@Validated
@Accessors(chain = true)
@ConfigurationProperties(prefix = "yeed-oss.oss")
public class OssProperties {

    /** 单文件上传大小上限：OSS 兜底约束（防内部调用方漏配）；公网业务侧可在各自接口设更小的限制 */
    @DataSizeUnit(DataUnit.MEGABYTES)
    private DataSize maxUploadSize = DataSize.ofMegabytes(30);

    /**
     * 文件保留时长：上传时按此刻 + 该时长算出 {@code expireTime}，到期由清理任务回收
     *
     * <p>{@code null} = 永久保存（Nacos 里把该键留空即可绑定为 null）；配 0 等同"立即过期"；
     * 非负约束见 {@link #isExpireAfterNonNegative()}
     */
    @DurationUnit(ChronoUnit.DAYS)
    private Duration expireAfter = Duration.ofDays(90);

    /**
     * 保留时长不得为负：会算出早于上传时刻的过期时间，文件一入库即可被回收（静默丢数据）
     *
     * <p>不用 {@code @Min}：它是数值约束，没有 {@link Duration} 的验证器实现，作用在
     * {@code Duration} 上会抛 {@code UnexpectedTypeException}（实测 HV000030），绑定期即启动失败。
     * {@link AssertTrue} 是 Jakarta 标准注解且只作用于 boolean，可安全承载这类条件约束
     */
    @AssertTrue(message = "yeed-oss.oss.expire-after 不能为负数")
    public boolean isExpireAfterNonNegative() {
        return expireAfter == null || !expireAfter.isNegative();
    }

    /** 本地磁盘存储配置（v1 唯一存储实现；阿里云等经 {@link OssStorageTypeEnum} 预留） */
    private Local local = new Local();


    /** 本地磁盘存储 */
    @Data
    @Accessors(chain = true)
    public static class Local {
        /** 存储根目录（objectKey 下的相对路径在其内展开；目录不存在时自动创建） */
        private String storagePath = "./file/oss";
    }

}
