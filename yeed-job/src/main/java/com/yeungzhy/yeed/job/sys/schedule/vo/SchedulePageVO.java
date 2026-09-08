package com.yeungzhy.yeed.job.sys.schedule.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yeungzhy.yeed.common.core.result.PageResult;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 定时计划分页出参：列表数据与全局维护模式状态一并返回
 *
 * <p>维护模式标记是全局状态而非某条计划的属性，塞不进 {@link ScheduleVO}；
 * 而 {@link PageResult} 是 common-core 的通用分页类型，不能为单个业务加字段，
 * 故在此做一层包装，让前端一次请求同时拿到两者，不必为渲染顶部横幅再发一次查询
 *
 * @author yeungzhy
 * @since 2026-09-08
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SchedulePageVO {

    /** 计划分页数据 */
    private PageResult<ScheduleVO> page;

    /** 是否处于全局维护模式，true 时全部实例停止自动触发 */
    private Boolean standby;

}
