package com.yeungzhy.yeed.oss.sys.file.dto;

import com.yeungzhy.yeed.oss.sys.file.enums.OssFileStatusEnum;
import com.yeungzhy.yeed.oss.sys.file.enums.OssStorageTypeEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 系统文件记录表 DTO（前端入参）
 *
 * @author yeungzhy
 * @since 2026-09-04 12:00:19
 */
@Data
@Accessors(chain = true)
public class OssFileDTO {

    // ================== 主键 ==================
    /** 雪花ID主键 */
    private Long id;

    // ================== 业务字段 ==================
    /** 展示主名（不含扩展名；扩展名由上传判真写入，不接受入参） */
    private String fileName;
    /** 存储对象key */
    private String objectKey;
    /** 存储类型:0-本地磁盘,1-阿里云OSS等 */
    private OssStorageTypeEnum storageType;
    /** 文件大小(字节) */
    private Long fileSize;
    /** MIME类型 */
    private String contentType;
    /** 业务编码 */
    private String bizCode;
    /** 文件内容MD5 */
    private String md5;
    /** 过期时间,null=永久保存;到期由OSS内部统一清理 */
    private LocalDateTime expireTime;
    /** 可用状态:0-已清理,1-可用 */
    private OssFileStatusEnum status;

    // ================== 审计字段 ==================
    /** 创建人 */
    private Long createBy;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新人 */
    private Long updateBy;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 删除人 */
    private Long deleteBy;
    /** 逻辑删除,0-未删,时间戳-已删 */
    private Long deleteTime;
    /** 乐观锁 */
    private Integer version;
    /** 扩展信息 */
    private Map<String, Object> extra;

}
