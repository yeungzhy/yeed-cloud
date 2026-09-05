package com.yeungzhy.yeed.job.sys.export.task.controller;

import com.yeungzhy.yeed.api.export.task.dto.ExportTaskPageDTO;
import com.yeungzhy.yeed.api.export.task.dto.ExportTaskSaveDTO;
import com.yeungzhy.yeed.api.export.task.vo.ExportTaskPageVO;
import com.yeungzhy.yeed.common.core.result.PageResult;
import com.yeungzhy.yeed.common.web.annotation.InternalApi;
import com.yeungzhy.yeed.job.sys.export.task.service.ExportTaskService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 导出任务表 内部接口控制器（仅供 admin 经 Feign 调用）
 *
 * <p>RPC-Style：成功返回裸数据；失败抛异常，由
 * {@code InternalApiExceptionHandler} 统一映射为 HTTP 错误码 + ApiResult body，
 * admin 侧经 {@code InternalErrorDecoder} 还原为 {@code BizException}。
 *
 * @author yeungzhy
 * @since 2026-09-01 21:55:57
 */
@Slf4j
@Validated
@InternalApi
@RestController
@RequestMapping("/internal/export/task")
public class ExportTaskInternalController {

    @Resource
    private ExportTaskService exportTaskService;


    /**
     * 新增
     *
     * @param dto 入参
     * @return 新增记录的主键 ID
     * @author yeungzhy
     * @since 2026-09-01 21:55:57
     */
    @PostMapping("/save")
    public Long save(@Valid @RequestBody ExportTaskSaveDTO dto) {
        return exportTaskService.save(dto);
    }


    /**
     * 分页查询
     *
     * @param dto 分页查询入参
     * @return 分页结果
     * @author yeungzhy
     * @since 2026-09-01 21:55:57
     */
    @PostMapping("/page")
    public PageResult<ExportTaskPageVO> page(@Valid @RequestBody ExportTaskPageDTO dto) {
        return exportTaskService.page(dto);
    }


}
