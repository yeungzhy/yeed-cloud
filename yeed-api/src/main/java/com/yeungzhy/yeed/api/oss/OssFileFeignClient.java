package com.yeungzhy.yeed.api.oss;

import com.yeungzhy.yeed.api.feign.config.InternalFeignConfig;
import com.yeungzhy.yeed.api.oss.dto.FileUploadQuery;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 内部文件上传客户端（job → oss）
 *
 * <p>传输契约：元数据走 Query、文件二进制走 Body（application/octet-stream）
 *
 * <ul>
 *   <li>两侧均为框架标准能力：Feign 侧 {@code @RequestBody byte[]} 由内置 ByteArrayEncoder 编码、
 *       {@code @SpringQueryMap} 自动展开 POJO 为 Query 参数并 URL 编码（中文文件名安全）；服务端
 *       {@code @ModelAttribute} 绑定 Query、{@code @RequestBody byte[]} 由 ByteArrayHttpMessageConverter
 *       处理（支持任意 media type），零额外依赖。</li>
 *   <li>Query 长度余量未触及容器/网关 Query 长度上限；Feign 调用便捷。</li>
 * </ul>
 *
 * <p>RPC-Style：成功返回裸数据（文件记录主键 ossId），失败抛业务异常
 *
 * <p>演进方向（v1 不做）：
 * <ul>
 *   <li>大文件/断点续传/失败重试：拆两步——先 {@code POST /files}
 *       建元数据记录拿 ossId，再{@code PUT /files/{ossId}/content} 传内容。</li>
 *   <li>对齐对象存储 Header 语义：元数据迁至 {@code X-Oss-Meta-*}
 *       自定义 Header（中文值需{@code URLEncoder} 编码，服务端解码）。</li>
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-08-22
 */
@FeignClient(
        name = "yeed-oss",
        contextId = "sysOssFeignClient",
        path = "/internal/oss",
        configuration = InternalFeignConfig.class
)
public interface OssFileFeignClient {

    /**
     * 上传文件：Query 元数据 + Body 二进制
     *
     * @param query    上传元数据（fileName 必填）
     * @param fileData 文件二进制内容
     * @return 文件记录主键（ossId）
     */
    @PostMapping(value = "/upload", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    Long upload(@SpringQueryMap FileUploadQuery query, @RequestBody byte[] fileData);

    /**
     * 下载文件：返回字节流与附件响应头
     *
     * @param id 文件记录主键（ossId）
     * @return 文件二进制 + Content-Type/Content-Disposition（attachment 中文文件名）
     */
    @GetMapping("/download/{id}")
    ResponseEntity<byte[]> download(@PathVariable Long id);

}
