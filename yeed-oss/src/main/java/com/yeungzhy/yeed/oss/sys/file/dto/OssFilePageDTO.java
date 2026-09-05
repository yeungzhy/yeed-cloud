package com.yeungzhy.yeed.oss.sys.file.dto;

import com.yeungzhy.yeed.common.core.request.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 系统文件记录表 分页查询 DTO（前端入参）
 *
 * @author yeungzhy
 * @since 2026-09-04 12:00:19
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class OssFilePageDTO extends PageRequest {


}
