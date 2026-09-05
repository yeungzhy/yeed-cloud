package com.yeungzhy.yeed.api.export.task.dto;

import com.yeungzhy.yeed.common.core.request.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 导出任务表分页查询入参(admin -> job)
 *
 * @author yeungzhy
 * @since 2026-08-22 17:08:53
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class ExportTaskPageDTO extends PageRequest {


}
