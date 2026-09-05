package com.yeungzhy.yeed.oss.sys.file.service;

import com.yeungzhy.yeed.oss.sys.file.entity.OssFile;

/**
 * 文件下载结果（文件记录 + 二进制内容）
 *
 * <p>service 层一次性返回元数据与字节流，避免 controller 为取 contentType / 拼下载名
 * 二次查询记录；contentType 可空，由调用方兜底 octet-stream。
 *
 * @param file         文件记录（contentType 供响应头使用）
 * @param downloadName 下载文件名（展示名主名 + 判真扩展名），已清洗，可安全写入 Content-Disposition
 * @param data         文件二进制内容
 * @author yeungzhy
 * @since 2026-08-23
 */
public record OssFileDownload(OssFile file, String downloadName, byte[] data) {}