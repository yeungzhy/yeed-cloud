package com.yeungzhy.yeed.job.sys.export.task.service;

import com.yeungzhy.yeed.api.export.task.dto.ExportTaskPageDTO;
import com.yeungzhy.yeed.api.export.task.dto.ExportTaskSaveDTO;
import com.yeungzhy.yeed.api.export.task.vo.ExportTaskPageVO;
import com.yeungzhy.yeed.common.core.result.PageResult;
import com.yeungzhy.yeed.job.sys.export.task.entity.ExportTask;

import java.util.List;

/**
 * 导出任务表 服务类
 *
 * @author yeungzhy
 * @since 2026-09-01 21:55:57
 */
public interface ExportTaskService {

    // ==================== 导出轮询认领 ====================

    /**
     * 认领待执行任务：把至多 {@code batchSize} 个 WAITING 任务原子地置为 RUNNING 并返回
     *
     * <p> 「查待执行 + 置执行中」必须作为一个原子操作：两个步骤之间若有其他执行器插入，
     * 会把同一批任务重复认领、重复导出。事务内先以 {@code SELECT ... FOR UPDATE} 悲观锁锁住
     * 候选行，再以 {@code WHERE status=WAITING} 的 CAS 条件更新，双保险下多实例/调度重叠不重复认领
     *
     * <p> 认领后任务即视为「已开始执行」：执行中的任务被异常打断（进程崩溃等）不会自动回退
     * WAITING，需依赖调度侧的崩溃自愈（超时重置），属调度能力范畴，此处不处理
     *
     * <p> 仅供导出轮询装配点按批取任务执行；任务「执行中」是长事务禁区，认领后须尽快提交事务，
     * 实际导出（Feign 拉数/写文件/上传）绝不能放在本方法所在事务内
     *
     * @param batchSize 单批认领上限（须为正数）
     * @return 认领成功的一批任务（已置 RUNNING）；无待执行任务时返回空列表
     */
    List<ExportTask> claimRunning(int batchSize);

    // ==================== 标准写入（CUD） ====================

    /**
     * 新增导出任务，只落库创建快照、不触发执行
     *
     * @param dto 创建入参，字段语义见 {@link ExportTaskSaveDTO}
     * @return 新增记录的主键 ID
     * @author yeungzhy
     * @since 2026-09-01 21:55:57
     */
    Long save(ExportTaskSaveDTO dto);

    /**
     * 分页查询导出任务
     *
     * @param dto 分页参数，暂无业务筛选字段，见 {@link ExportTaskPageDTO}
     * @return 分页结果；无命中返回空页而非 null
     * @author yeungzhy
     * @since 2026-09-01 21:55:57
     */
    PageResult<ExportTaskPageVO> page(ExportTaskPageDTO dto);


}
