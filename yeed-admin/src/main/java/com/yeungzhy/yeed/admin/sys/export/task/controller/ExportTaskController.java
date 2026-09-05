package com.yeungzhy.yeed.admin.sys.export.task.controller;

import com.yeungzhy.yeed.api.export.task.ExportTaskFeignClient;
import com.yeungzhy.yeed.api.export.task.dto.ExportTaskPageDTO;
import com.yeungzhy.yeed.api.export.task.vo.ExportTaskPageVO;
import com.yeungzhy.yeed.api.oss.OssFileFeignClient;
import com.yeungzhy.yeed.common.core.result.ApiResult;
import com.yeungzhy.yeed.common.core.result.PageResult;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 导出任务表 前端控制器
 *
 * @author yeungzhy
 * @since 2026-09-01 21:55:57
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/sys/export/task")
public class ExportTaskController {

    @Resource
    private ExportTaskFeignClient exportTaskFeignClient;
    @Resource
    private OssFileFeignClient ossFileFeignClient;


    /**
     * 分页查询
     *
     * @param dto 分页查询入参
     * @return 分页结果
     * @author yeungzhy
     * @since 2026-09-01 21:55:57
     */
    @PostMapping("/page")
    public ApiResult<PageResult<ExportTaskPageVO>> page(@Valid @RequestBody ExportTaskPageDTO dto) {
        return ApiResult.ok(exportTaskFeignClient.page(dto));
    }


    /**
     * 下载文件：返回字节流与附件响应头
     *
     * @param id 文件记录主键（ossId）
     * @return 文件二进制 + Content-Type/Content-Disposition（attachment 中文文件名）
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        return ossFileFeignClient.download(id);
    }



}
