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

    /** 文件名(不含扩展名) */
    private String fileName;
    /** 扩展名(含点) */
    private String fileExt;
    /** MIME类型 */
    private String contentType;
    /** 文件大小(字节) */
    private Integer fileSize;
    /** 文件内容MD5 */
    private String md5;

    /** 存储类型:0-本地磁盘,1-阿里云OSS等 */
    private OssStorageTypeEnum storageType;
    /** 存储对象key */
    private String objectKey;

    /** 业务编码 */
    private String bizCode;
    /** 可用状态:0-已清理,1-可用 */
    private OssFileStatusEnum status;
    /** 过期时间,null=永久保存;由OSS内部统一清理 */
    private LocalDateTime expireTime;

    /**
     * 完整文件名 = 主名 + 判真扩展名（列表展示与下载 Content-Disposition 共用）
     */
    public String displayName() {
        return fileName + fileExt;
    }

}
