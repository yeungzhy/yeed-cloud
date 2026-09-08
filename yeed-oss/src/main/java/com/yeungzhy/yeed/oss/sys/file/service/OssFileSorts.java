package com.yeungzhy.yeed.oss.sys.file.service;

import com.yeungzhy.yeed.oss.sys.file.entity.OssFile;
import com.yeungzhy.yeed.common.data.model.BaseEntity;
import com.yeungzhy.yeed.common.data.request.BaseSorts;
import org.springframework.stereotype.Component;

/**
 * 系统文件记录表 排序字段白名单
 *
 * @author yeungzhy
 * @since 2026-09-04 12:00:19
 */
@Component
public class OssFileSorts extends BaseSorts<OssFile> {

    protected OssFileSorts() {
        super(OssFile.class, new BaseSorts.Builder<OssFile>()
                .add(BaseEntity.Fields.createTime, OssFile::getCreateTime)
                // 多字段排序时必须带主键做决胜字段，否则排序键相同的行在翻页时可能错位或漏行
                .add(BaseEntity.Fields.id, OssFile::getId)
        );
    }

}
