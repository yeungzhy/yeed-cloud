package com.yeungzhy.yeed.job.sys.export.task.service;

import com.yeungzhy.yeed.api.export.task.dto.ExportTaskPageDTO;
import com.yeungzhy.yeed.api.export.task.dto.ExportTaskSaveDTO;
import com.yeungzhy.yeed.api.export.task.vo.ExportTaskPageVO;
import com.yeungzhy.yeed.common.core.result.PageResult;

/**
 * 导出任务表 服务类
 *
 * @author yeungzhy
 * @since 2026-09-01 21:55:57
 */
public interface ExportTaskService {

    // ==================== 标准写入（CUD） ====================

    /**
     * 新增
     *
     * @param dto 入参
     * @return 新增记录的主键 ID
     * @author yeungzhy
     * @since 2026-09-01 21:55:57
     */
    Long save(ExportTaskSaveDTO dto);

    /**
     * 分页查询
     *
     * @param dto 分页查询入参
     * @return 分页结果
     * @author yeungzhy
     * @since 2026-09-01 21:55:57
     */
    PageResult<ExportTaskPageVO> page(ExportTaskPageDTO dto);


}
