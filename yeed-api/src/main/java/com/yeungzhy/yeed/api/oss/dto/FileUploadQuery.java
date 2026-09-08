package com.yeungzhy.yeed.api.oss.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 内部上传接口 Query 参数（job → oss）
 *
 * <p>走 Query 而非 Header：{@code @SpringQueryMap} 直接把 POJO 展开并 URL 编码，中文文件名无需额外处理
 *
 * @author yeungzhy
 * @since 2026-08-23
 */
@Data
@Accessors(chain = true)
public class FileUploadQuery {

    /** 业务编码，用于归档分组，可为空 */
    private String bizCode;
    /** 原始文件名，不能为空（含扩展名）；落库的 fileExt 由服务端判真得出，不采信此处的值 */
    private String fileName;

}
