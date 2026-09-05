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
                // 多个字段排序时,要加上主键作为决胜字段,防止在其他字段相同的发生数据错乱或漏页
                .add(BaseEntity.Fields.id, OssFile::getId)
        );
    }

}
