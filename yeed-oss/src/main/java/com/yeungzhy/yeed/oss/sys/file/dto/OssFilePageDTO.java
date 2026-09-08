package com.yeungzhy.yeed.oss.sys.file.dto;

import com.yeungzhy.yeed.common.core.request.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 系统文件记录表 分页查询 DTO（前端入参）
 *
 * <p>暂无业务筛选字段，只继承分页与排序参数；本服务当前只有内部上传下载接口，还没有列表页
 *
 * @author yeungzhy
 * @since 2026-09-04 12:00:19
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class OssFilePageDTO extends PageRequest {


}
