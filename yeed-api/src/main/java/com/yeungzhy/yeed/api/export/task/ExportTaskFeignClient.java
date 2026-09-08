package com.yeungzhy.yeed.api.export.task;

import com.yeungzhy.yeed.api.export.task.dto.ExportTaskPageDTO;
import com.yeungzhy.yeed.api.export.task.vo.ExportTaskPageVO;
import com.yeungzhy.yeed.api.export.task.dto.ExportTaskSaveDTO;
import com.yeungzhy.yeed.api.feign.config.InternalFeignConfig;
import com.yeungzhy.yeed.common.core.result.PageResult;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 导出任务内部 Feign 契约（消费方：yeed-admin；提供方：yeed-job）
 *
 * <p>RPC-Style：方法签名直接返回裸数据，成功才返回、失败由 {@code InternalErrorDecoder} 解码为
 * {@link com.yeungzhy.yeed.common.core.exception.BizException BizException} 中断调用，
 * 调用方无需（也无法）判 ApiResult 码
 *
 * @author yeungzhy
 * @since 2026-08-23
 */
@FeignClient(
        name = "yeed-job",
        contextId = "exportTaskFeignClient",
        path = "/internal/export/task",
        configuration = InternalFeignConfig.class
)
public interface ExportTaskFeignClient {


    /**
     * 创建导出任务
     *
     * @param dto 任务入参，exportType 决定 job 侧执行器路由、为空则任务无法执行；
     *            queryParam 是查询条件快照 JSON，创建时即固化，可为 null
     * @return 新增记录的主键 ID；失败抛异常（不返回）
     * @author yeungzhy
     * @since 2026-08-22 17:08:53
     */
    @PostMapping("/save")
    Long save(@Valid @RequestBody ExportTaskSaveDTO dto);


    /**
     * 分页查询导出任务
     *
     * @param dto 分页入参，当前无业务筛选条件；分页参数不能为 null
     * @return 分页结果，无命中返回空页；失败抛异常（不返回）
     * @author yeungzhy
     * @since 2026-08-22 17:08:53
     */
    @PostMapping("/page")
    PageResult<ExportTaskPageVO> page(@Valid @RequestBody ExportTaskPageDTO dto);



}
