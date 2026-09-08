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
 * 导出任务 前端控制器
 *
 * <p>本模块不持有导出数据，列表与下载都转调 yeed-job（任务）与 yeed-oss（文件）
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
     * 分页查询导出任务（进度与结果文件名在此查看）
     *
     * @param dto 分页与筛选入参，筛选字段为 null 即不参与过滤
     * @return 分页结果；job 不可达时抛异常，不返回空数据掩盖故障
     * @author yeungzhy
     * @since 2026-09-01 21:55:57
     */
    @PostMapping("/page")
    public ApiResult<PageResult<ExportTaskPageVO>> page(@Valid @RequestBody ExportTaskPageDTO dto) {
        return ApiResult.ok(exportTaskFeignClient.page(dto));
    }


    /**
     * 下载导出文件
     *
     * <p>直接透传 oss 的响应（含 Content-Type 与 attachment 头）；文件整体读进内存的代价在 oss 侧，
     * 本服务只做转发
     *
     * @param id 文件记录主键（ossId），不能为空
     * @return 文件字节流 + 响应头；文件不存在时由 oss 抛异常
     * @author yeungzhy
     * @since 2026-09-01 21:55:57
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        return ossFileFeignClient.download(id);
    }



}
