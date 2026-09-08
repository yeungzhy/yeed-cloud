package com.yeungzhy.yeed.oss.sys.file.controller;

import com.yeungzhy.yeed.api.oss.dto.FileUploadQuery;
import com.yeungzhy.yeed.common.web.annotation.InternalApi;
import com.yeungzhy.yeed.oss.sys.file.service.OssFileDownload;
import com.yeungzhy.yeed.oss.sys.file.service.OssFileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * 系统文件 内部控制器（job → oss）
 *
 * <p>只服务于内部调用：挂载 {@code /internal/**}，网关不为该前缀定义外部路由，
 * 外部拿不到上传下载入口，文件一律经业务服务（admin / job）代转
 *
 * @author yeungzhy
 * @since 2026-09-04 12:00:19
 */
@Slf4j
@Validated
@InternalApi
@RestController
@RequestMapping("/internal/oss")
public class OssFileInternalController {

    @Resource
    private OssFileService ossFileService;


    /**
     * 上传文件：Query 元数据 + Body 二进制
     *
     * @param query    上传元数据，fileName 不能为空
     * @param fileData 文件二进制内容，不能为 null
     * @return 文件记录主键（ossId）
     */
    @PostMapping(value = "/upload", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Long upload(@ModelAttribute FileUploadQuery query, @RequestBody byte[] fileData) {
        return ossFileService.upload(query, fileData);
    }

    /**
     * 下载文件：返回字节流与附件响应头
     *
     * @param id 文件记录主键（ossId），不能为 null
     * @return 文件二进制 + Content-Type / Content-Disposition（attachment，下载名已清洗）
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        OssFileDownload result = ossFileService.download(id);
        String contentType = result.file().getContentType();

        // 下载名由 service 给出（展示名主名 + 判真扩展名），已清洗，可直接写响应头
        ContentDisposition disposition = ContentDisposition
                .attachment() // 指定为附件下载
                .filename(result.downloadName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(result.data());
    }

    /**
     * 删除文件
     *
     * <p>幂等语义在 service 侧：先删存储对象（不存在视为已清理），再逻辑删除记录
     *
     * @param id 文件记录主键（ossId），不能为 null
     */
    @DeleteMapping("/delete/{id}")
    public void delete(@PathVariable Long id) {
        ossFileService.delete(id);
    }


}
