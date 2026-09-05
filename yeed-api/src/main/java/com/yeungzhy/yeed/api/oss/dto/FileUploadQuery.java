package com.yeungzhy.yeed.api.oss.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 内部上传接口 Query 参数（job → oss）
 *
 * @author yeungzhy
 * @since 2026-08-23
 */
@Data
@Accessors(chain = true)
public class FileUploadQuery {

    /** 业务编码 */
    private String bizCode;
    /** 原始文件名 */
    private String fileName;

}
