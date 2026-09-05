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
 * <p>RPC-Style：方法签名直接返回裸数据，成功才返回、失败由
 * {@code InternalErrorDecoder} 解码为 {@link com.yeungzhy.yeed.common.core.exception.BizException BizException}
 * 中断调用——调用方无需（也无法）判 ApiResult 码。
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
     * 新增
     *
     * @param dto 入参
     * @return 新增记录的主键 ID；失败抛异常（不返回）
     * @author yeungzhy
     * @since 2026-08-22 17:08:53
     */
    @PostMapping("/save")
    Long save(@Valid @RequestBody ExportTaskSaveDTO dto);


    /**
     * 分页查询
     *
     * @param dto 分页查询入参
     * @return 分页结果；失败抛异常（不返回）
     * @author yeungzhy
     * @since 2026-08-22 17:08:53
     */
    @PostMapping("/page")
    PageResult<ExportTaskPageVO> page(@Valid @RequestBody ExportTaskPageDTO dto);



}
