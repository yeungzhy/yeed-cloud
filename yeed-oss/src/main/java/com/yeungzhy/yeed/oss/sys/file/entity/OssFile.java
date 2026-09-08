package com.yeungzhy.yeed.oss.sys.file.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yeungzhy.yeed.common.data.model.BaseEntity;
import com.yeungzhy.yeed.oss.sys.file.enums.OssFileStatusEnum;
import com.yeungzhy.yeed.oss.sys.file.enums.OssStorageTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 系统文件记录表
 *
 * @author yeungzhy
 * @since 2026-09-04 12:00:19
 */
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName(value = "yeed_sys_oss", autoResultMap = true)
public class OssFile extends BaseEntity {

    /** 文件名（不含扩展名） */
    private String fileName;
    /** 扩展名（含点），由上传时的字节判真写入，不接受入参 */
    private String fileExt;
    /** MIME 类型 */
    private String contentType;
    /** 文件大小（字节） */
    private Long fileSize;
    /** 文件内容 MD5 */
    private String md5;

    /** 存储类型：0-本地磁盘，1-阿里云OSS */
    private OssStorageTypeEnum storageType;
    /** 存储对象 key */
    private String objectKey;

    /** 业务编码 */
    private String bizCode;
    /** 可用状态：0-已清理，1-可用 */
    private OssFileStatusEnum status;
    /** 过期时间，null=永久保存；由 OSS 内部统一清理 */
    private LocalDateTime expireTime;

    /**
     * 完整文件名 = 主名 + 判真扩展名
     *
     * <p>列表展示与下载 Content-Disposition 共用这一个出口，避免两处各自拼、拼法漂移
     *
     * @return 完整文件名，如 {@code 用户导出.xlsx}
     */
    public String displayName() {
        return fileName + fileExt;
    }

}
