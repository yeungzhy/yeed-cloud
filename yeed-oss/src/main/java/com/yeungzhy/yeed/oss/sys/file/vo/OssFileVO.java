package com.yeungzhy.yeed.oss.sys.file.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yeungzhy.yeed.oss.sys.file.enums.OssFileStatusEnum;
import com.yeungzhy.yeed.oss.sys.file.enums.OssStorageTypeEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 系统文件记录表 VO（返回出参）
 *
 * @author yeungzhy
 * @since 2026-09-04 12:00:19
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OssFileVO {

    // ================== 主键 ==================
    /** 雪花 ID 主键 */
    private Long id;

    // ================== 业务字段 ==================
    /** 完整展示名（主名 + 判真扩展名，由 {@code OssFile#displayName()} 拼出，非入参原名） */
    private String fileName;
    /** 存储对象 key */
    private String objectKey;
    /** 存储类型：0-本地磁盘，1-阿里云OSS */
    private OssStorageTypeEnum storageType;
    /** 文件大小（字节） */
    private Long fileSize;
    /** MIME 类型 */
    private String contentType;
    /** 业务编码 */
    private String bizCode;
    /** 文件内容 MD5 */
    private String md5;
    /** 过期时间，null=永久保存；到期由 OSS 内部统一清理 */
    private LocalDateTime expireTime;
    /** 可用状态：0-已清理，1-可用 */
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
    /** 逻辑删除，0-未删，时间戳-已删 */
    private Long deleteTime;
    /** 乐观锁 */
    private Integer version;
    /** 扩展信息 */
    private Map<String, Object> extra;

}
