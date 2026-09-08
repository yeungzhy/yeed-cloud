package com.yeungzhy.yeed.oss.sys.file.service;

import com.yeungzhy.yeed.api.oss.dto.FileUploadQuery;
import com.yeungzhy.yeed.common.core.exception.BizException;

/**
 * 系统文件记录表 服务门面
 *
 * <p>写入侧的三条不变量集中在此：
 * <ul>
 *   <li>扩展名只由字节判真得出，绝不采信入参（见 {@code OssFileConvert#toEntity} 的 ignore）
 *   <li>下载与删除都先校验记录归属与状态，非超管只能操作自己上传的文件
 *   <li>存储对象与记录允许短暂不一致，故删除走"先删对象、再逻辑删记录"的幂等顺序
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-09-04 12:00:19
 */
public interface OssFileService {

    /**
     * 上传文件（写本地磁盘 + 落记录）
     *
     * @param query 上传元数据，fileName 不能为空
     * @param data  文件二进制内容，不能为 null 或空数组
     * @return 文件记录主键（ossId）
     * @throws BizException data 为空 / fileName 为空 / 超出大小上限 / 类型无法判真
     */
    Long upload(FileUploadQuery query, byte[] data);

    /**
     * 下载文件（按 ossId 读取记录与内容）
     *
     * @param id 文件记录主键（ossId），不能为 null
     * @return 文件记录 + 二进制内容
     * @throws BizException 记录不存在或状态不可用（CommonCode.NOT_FOUND）
     */
    OssFileDownload download(Long id);

    /**
     * 删除文件（先删存储对象幂等，再逻辑删除记录）
     *
     * @param id 文件记录主键（ossId），不能为 null
     * @throws BizException 记录不存在或状态不可用（CommonCode.NOT_FOUND）
     */
    void delete(Long id);

}
