package com.yeungzhy.yeed.oss.sys.file.mapper;

import com.yeungzhy.yeed.common.data.mybatis.BaseMapper;
import com.yeungzhy.yeed.oss.sys.file.entity.OssFile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统文件记录表 Mapper 接口
 *
 * @author yeungzhy
 * @since 2026-09-04 12:00:19
 */
@Mapper
public interface OssFileMapper extends BaseMapper<OssFile> {


}
