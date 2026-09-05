package com.yeungzhy.yeed.oss.sys.file.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yeungzhy.yeed.api.oss.dto.FileUploadQuery;
import com.yeungzhy.yeed.common.core.enums.FileTypeEnum;
import com.yeungzhy.yeed.common.core.exception.BizException;
import com.yeungzhy.yeed.common.core.result.ApiResult;
import com.yeungzhy.yeed.common.core.security.LoginUserHelper;
import com.yeungzhy.yeed.common.core.support.FileTypeProbeUtil;
import com.yeungzhy.yeed.common.core.support.FileUtil;
import com.yeungzhy.yeed.oss.sys.file.config.OssProperties;
import com.yeungzhy.yeed.oss.sys.file.entity.OssFile;
import com.yeungzhy.yeed.oss.sys.file.enums.OssFileStatusEnum;
import com.yeungzhy.yeed.oss.sys.file.enums.OssStorageTypeEnum;
import com.yeungzhy.yeed.oss.sys.file.mapper.OssFileMapper;
import com.yeungzhy.yeed.oss.sys.file.service.OssFileConvert;
import com.yeungzhy.yeed.oss.sys.file.service.OssFileDownload;
import com.yeungzhy.yeed.oss.sys.file.service.OssFileService;
import com.yeungzhy.yeed.oss.sys.file.service.OssFileSorts;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 系统文件记录表 服务实现类
 *
 * @author yeungzhy
 * @since 2026-09-04 12:00:19
 */
@Slf4j
@Service
public class OssFileServiceImpl implements OssFileService {

    /** 本地磁盘日期分目录格式 */
    private static final DateTimeFormatter DATE_PATH_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * 主名最大长度（字符数）：取值同时受 {@code yeed_sys_oss.file_name} 列宽与下载响应头长度约束，
     * 改小安全，改大须核实这两处上限
     */
    private static final int MAX_FILE_NAME_LENGTH = 20;

    @Resource
    private OssFileSorts ossFileSorts;
    @Resource
    private OssFileMapper ossFileMapper;
    @Resource
    private OssFileConvert ossFileConvert;
    @Resource
    private OssProperties ossProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long upload(FileUploadQuery query, byte[] data) {
        // 空与大小校验（OSS 兜底约束）
        long maxUploadSize = ossProperties.getMaxUploadSize().toBytes();
        if (data == null || data.length == 0) {
            throw new BizException("上传文件内容为空");
        }
        if (data.length > maxUploadSize) {
            throw new BizException("上传文件大小超过上限（%s）"
                    .formatted(FileUtil.formatFileSize(maxUploadSize)));
        }
        // 下载名依赖 fileName，必填；清洗后为空等同缺失（如 "///"、纯控制字符）
        String mainName = FileUtil.resolveMainName(query.getFileName(), MAX_FILE_NAME_LENGTH);
        if (mainName == null) {
            throw new BizException("上传文件名称为空");
        }

        // 根据文件字节判断文件类型，判真结果同时决定存储扩展名、回放 MIME 与下载扩展名
        FileTypeEnum type = FileTypeProbeUtil.probe(data);
        String fileExt = type.getExtension();

        // 生成 objectKey 并写本地磁盘
        String objectKey = buildObjectKey(fileExt);
        FileUtil.writeBytes(resolveLocalPath(objectKey), data);

        // 落文件记录
        OssFile file = OssFile.builder()
                .fileName(mainName)
                .fileExt(fileExt)
                .objectKey(objectKey)
                .storageType(OssStorageTypeEnum.LOCAL_DISK)
                .fileSize(data.length)
                .contentType(type.getMimeType())
                .expireTime(resolveExpireTime(ossProperties.getExpireAfter()))
                .status(OssFileStatusEnum.AVAILABLE)
                .build();
        ossFileMapper.insert(file);

        log.info("文件上传成功: ossId={}, objectKey={}, size={}, type={}",
                file.getId(), objectKey, data.length, type.getMimeType());
        return file.getId();
    }

    @Override
    public OssFileDownload download(Long id) {
        OssFile file = requireAvailable(id);
        byte[] data = FileUtil.readBytes(resolveLocalPath(file.getObjectKey()));
        return new OssFileDownload(file, file.displayName(), data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OssFile file = requireAvailable(id);

        // 1. 删存储对象（幂等：对象不存在视为已清理，不阻断）
        FileUtil.deleteIfExists(resolveLocalPath(file.getObjectKey()));
        // 2. 逻辑删除记录（本服务不解析会话，deleteBy=0L 系统操作）
        ossFileMapper.logicDeleteById(id, ossFileMapper.nanoEpoch(), 0L);

        log.info("文件已删除: ossId={}, objectKey={}", id, file.getObjectKey());
    }


    // ==================== 私有方法 ====================

    /**
     * 查询可用记录（归属校验 + 状态可用），否则抛 404
     *
     * <p>归属条件只在"已登录且非超管"时拼：超管看全部，无登录上下文（内部调用、定时任务）不做归属校验
     */
    private OssFile requireAvailable(Long id) {
        OssFile file = ossFileMapper.selectOne(Wrappers.<OssFile>lambdaQuery()
                .eq(OssFile::getId, id)
                .eq(LoginUserHelper.isLogin() && !LoginUserHelper.isSuperAdmin(),
                        OssFile::getCreateBy, LoginUserHelper.getUserId(null))
        );
        if (file == null || file.getStatus() != OssFileStatusEnum.AVAILABLE) {
            throw new BizException(ApiResult.CommonCode.NOT_FOUND);
        }
        return file;
    }


    /** 生成 objectKey：{@code yyyy/MM/dd}/{UUID}{判真扩展名}（扩展名来自 {@link FileTypeProbeUtil}，非入参） */
    private String buildObjectKey(String extension) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return LocalDate.now().format(DATE_PATH_FORMAT) + "/" + uuid + extension;
    }

    /** 解析 objectKey 到本地磁盘绝对路径（根目录取自本地存储配置） */
    private Path resolveLocalPath(String objectKey) {
        return FileUtil.resolveAbsolutePath(ossProperties.getLocal().getStoragePath(), objectKey);
    }

    /** 计算过期时间：保留时长为 {@code null} 表示永久保存，落库 {@code null} */
    private LocalDateTime resolveExpireTime(Duration expireAfter) {
        return expireAfter == null ? null : LocalDateTime.now().plus(expireAfter);
    }


}
