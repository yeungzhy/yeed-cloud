package com.yeungzhy.yeed.api.export.task.dto;

import com.yeungzhy.yeed.api.export.ExportTypeEnum;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 创建导出任务 DTO（admin → job）
 *
 * <p>任务只是「待办」，创建时不含查询对象：查询条件序列化成 {@code queryParam} 快照一并落库，
 * 避免导出跑到一半因前端改筛选条件而变口径
 *
 * @author yeungzhy
 * @since 2026-08-22 17:08:53
 */
@Data
@Accessors(chain = true)
public class ExportTaskSaveDTO {

    /** 导出业务类型，决定 job 侧按 {@code ExporterRegistry} 路由到哪个执行器 */
    private ExportTypeEnum exportType;
    /** 导出条件参数（JSON），创建时固化，执行时透传给数据提供方 */
    private String queryParam;
    /** 导出文件名（含扩展名），创建时由业务侧生成 */
    private String fileName;

}
