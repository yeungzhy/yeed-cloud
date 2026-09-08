package com.yeungzhy.yeed.api.export.task.dto;

import com.yeungzhy.yeed.common.core.request.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 导出任务 分页查询入参（admin → job）
 *
 * <p>暂无业务筛选条件，只继承分页参数；后续加筛选字段只改本类，不改 Feign 方法签名
 *
 * @author yeungzhy
 * @since 2026-08-22 17:08:53
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class ExportTaskPageDTO extends PageRequest {


}
