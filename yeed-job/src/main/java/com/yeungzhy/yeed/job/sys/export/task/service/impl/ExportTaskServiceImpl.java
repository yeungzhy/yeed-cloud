package com.yeungzhy.yeed.job.sys.export.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yeungzhy.yeed.api.export.task.dto.ExportTaskPageDTO;
import com.yeungzhy.yeed.api.export.task.dto.ExportTaskSaveDTO;
import com.yeungzhy.yeed.api.export.task.vo.ExportTaskPageVO;
import com.yeungzhy.yeed.common.core.result.PageResult;
import com.yeungzhy.yeed.common.core.security.LoginUserHelper;
import com.yeungzhy.yeed.job.sys.export.task.entity.ExportTask;
import com.yeungzhy.yeed.job.sys.export.task.enums.ExportTaskStatusEnum;
import com.yeungzhy.yeed.job.sys.export.task.mapper.ExportTaskMapper;
import com.yeungzhy.yeed.job.sys.export.task.service.ExportTaskConvert;
import com.yeungzhy.yeed.job.sys.export.task.service.ExportTaskService;
import com.yeungzhy.yeed.job.sys.export.task.service.ExportTaskSorts;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 导出任务表 服务实现类
 *
 * @author yeungzhy
 * @since 2026-09-01 21:55:57
 */
@Slf4j
@Service
public class ExportTaskServiceImpl implements ExportTaskService {

    @Resource
    private ExportTaskSorts exportTaskSorts;
    @Resource
    private ExportTaskMapper exportTaskMapper;
    @Resource
    private ExportTaskConvert exportTaskConvert;


    @Override
    @Transactional
    public List<ExportTask> claimRunning(int batchSize) {
        // 悲观锁锁定候选行（排序取最早创建的批），直到本事务提交前其他执行器读不到/改不动这 N 行
        List<ExportTask> candidates = exportTaskMapper.selectList(Wrappers.<ExportTask>lambdaQuery()
                .eq(ExportTask::getStatus, ExportTaskStatusEnum.WAITING)
                .orderByAsc(ExportTask::getId)
                .last("LIMIT " + batchSize + " FOR UPDATE"));
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<Long> ids = candidates.stream().map(ExportTask::getId).toList();
        LocalDateTime now = LocalDateTime.now();
        exportTaskMapper.update(null, Wrappers.<ExportTask>lambdaUpdate()
                // CAS 双保险：仅当行仍是 WAITING 才置 RUNNING（与 FOR UPDATE 互为冗余，防未来锁策略调整）
                .eq(ExportTask::getStatus, ExportTaskStatusEnum.WAITING)
                .in(ExportTask::getId, ids)
                .set(ExportTask::getStatus, ExportTaskStatusEnum.RUNNING.getCode())
                .set(ExportTask::getStartTime, now)
                .set(ExportTask::getUpdateTime, now)
        );
        // 内存态同步为 RUNNING，避免下游误读返回值里的 WAITING 快照
        candidates.forEach(task -> task.setStatus(ExportTaskStatusEnum.RUNNING).setStartTime(now));
        return candidates;
    }


    @Override
    public Long save(ExportTaskSaveDTO dto) {
        ExportTask entity = exportTaskConvert.toEntity(dto);
        exportTaskMapper.insert(entity);
        return entity.getId();
    }


    @Override
    public PageResult<ExportTaskPageVO> page(ExportTaskPageDTO dto) {
        LambdaQueryWrapper<ExportTask> lambdaQuery = Wrappers.<ExportTask>lambdaQuery()
                // 不是超管, 则只查询当前用户创建的导出任务
                .eq(!LoginUserHelper.isSuperAdmin(), ExportTask::getCreateBy, LoginUserHelper.getUserId());

        // 应用排序：先单字段 → 再多字段(顺序敏感)；默认降序；白名单外字段静默忽略
        exportTaskSorts.applyAll(lambdaQuery, dto.getOrderField(), dto.getIsAsc(), dto.getOrders());

        return exportTaskMapper.selectPageResult(dto, lambdaQuery, exportTaskConvert::toVO);
    }


}
