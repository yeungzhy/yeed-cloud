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
 * <p>传输契约：元数据走 Query，文件二进制走 Body（{@code application/octet-stream}）
 *
 * <p>两侧都是框架标准能力，无需额外依赖：
 * <ul>
 *   <li>Feign 侧：{@code @RequestBody byte[]} 走内置 ByteArrayEncoder，
 *       {@code @SpringQueryMap} 把 POJO 展开成 Query 参数并 URL 编码，中文文件名也安全
 *   <li>oss 侧：{@code @ModelAttribute} 绑 Query，{@code @RequestBody byte[]} 由
 *       ByteArrayHttpMessageConverter 处理
 * </ul>
 *
 * <p>RPC-Style：成功返回裸数据（文件记录主键 ossId），失败抛业务异常
 *
 * <p>大文件的断点续传与失败重试不走本接口，需拆成「先建元数据记录拿 ossId、再单独传内容」两步
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
     * @param query    上传元数据，fileName 不能为空；bizCode 用于归档分组，可为空
     * @param fileData 文件二进制内容，不能为 null
     * @return 文件记录主键（ossId）；失败抛异常，不返回
     */
    @PostMapping(value = "/upload", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    Long upload(@SpringQueryMap FileUploadQuery query, @RequestBody byte[] fileData);

    /**
     * 下载文件：返回字节流与附件响应头
     *
     * @param id 文件记录主键（ossId），不能为 null
     * @return 文件二进制 + Content-Type / Content-Disposition（attachment，下载名已清洗）
     */
    @GetMapping("/download/{id}")
    ResponseEntity<byte[]> download(@PathVariable Long id);

}
