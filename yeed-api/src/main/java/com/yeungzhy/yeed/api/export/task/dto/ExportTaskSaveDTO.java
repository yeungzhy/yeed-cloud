package com.yeungzhy.yeed.api.export.task.dto;

import com.yeungzhy.yeed.api.export.ExportTypeEnum;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 创建导出任务 DTO（admin -> job）
 *
 * @author yeungzhy
 * @since 2026-08-22 17:08:53
 */
@Data
@Accessors(chain = true)
public class ExportTaskSaveDTO {

    /** 导出业务类型 */
    private ExportTypeEnum exportType;
    /** 导出条件参数(JSON),执行时透传给数据提供方 */
    private String queryParam;
    /** 导出文件名(含扩展名),创建时由业务侧生成 */
    private String fileName;

}
