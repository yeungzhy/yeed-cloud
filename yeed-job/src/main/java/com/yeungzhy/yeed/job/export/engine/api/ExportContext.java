package com.yeungzhy.yeed.job.export.engine.api;

import org.springframework.util.Assert;

/**
 * 导出上下文，贯穿全流程
 *
 * <p> 由装配点（任务认领方）从任务实体映射而来
 *
 * <p> {@code param} 是本次任务共享的唯一实例：分页模式下导出器可就地覆写其分页字段，
 * 引擎串行翻页，故这样是安全的
 *
 * @param taskId        任务主键，引擎据此回写进度与成败状态
 * @param fileName      导出文件名，上传 OSS 的元数据
 * @param watermarkText 水印文本；创建人未知（系统触发）时为 null，水印处理器据此跳过绘制
 * @param param         业务查询参数，已由装配点按接口实现类声明反序列化；无查询条件的导出为 null
 * @param <P>           业务查询参数类型，由接口实现类声明
 * @author yeungzhy
 * @since 2026-09-07
 * @see Exporter
 */
public record ExportContext<P>(Long taskId, String fileName, String watermarkText, P param) {

    public ExportContext {
        Assert.notNull(taskId, "导出上下文 taskId 不能为空");
        Assert.hasText(fileName, "导出上下文 fileName 不能为空");
    }

}
