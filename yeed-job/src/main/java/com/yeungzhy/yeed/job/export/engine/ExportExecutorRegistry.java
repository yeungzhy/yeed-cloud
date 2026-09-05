package com.yeungzhy.yeed.job.export.engine;

import com.yeungzhy.yeed.api.export.ExportTypeEnum;
import com.yeungzhy.yeed.job.sys.export.task.entity.ExportTask;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 导出执行器注册表（按导出类型路由）
 *
 * <p>构造器注入自动收集容器内全部 {@link ExportExecutor} 实现，再按各实现自报的
 * {@link ExportExecutor#getExportType()} 重建路由表——不用 bean 名作键，
 * 避免 bean 名与 {@link ExportTypeEnum} 的 key 各自漂移后对不上。
 *
 * <p>启动期 fail-fast，两类错误都在构造时暴露，不会拖到用户点了导出按钮才发现：
 * <ul>
 *   <li>重复：同一导出类型有多个实现；</li>
 *   <li>缺失：{@link ExportTypeEnum#values()} 中任一类型没有实现——「枚举加了类型、没写执行器」。</li>
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-08-22
 * @see ExportExecutor
 */
@Component
public class ExportExecutorRegistry {

    /** 导出类型 key → 执行器（构造时一次性构建，之后只读） */
    private final Map<String, ExportExecutor<?, ?>> executorByType;

    /**
     * 收集全部执行器并按导出类型重建路由表
     *
     * @param executors Spring 自动收集的全部 {@link ExportExecutor} 实现，允许为空集合
     * @throws IllegalStateException 同一导出类型存在多个实现，或 {@link ExportTypeEnum} 中存在类型没有实现
     */
    public ExportExecutorRegistry(List<ExportExecutor<?, ?>> executors) {
        Map<String, ExportExecutor<?, ?>> byType = new HashMap<>();
        for (ExportExecutor<?, ?> executor : executors) {
            String typeKey = executor.getExportType().getKey();
            ExportExecutor<?, ?> existed = byType.putIfAbsent(typeKey, executor);
            if (existed != null) {
                throw new IllegalStateException("启动异常：导出类型 [" + typeKey + "] 存在多个 ExportExecutor 实现："
                        + existed.getClass().getName() + " 与 " + executor.getClass().getName());
            }
        }
        for (ExportTypeEnum type : ExportTypeEnum.values()) {
            if (!byType.containsKey(type.getKey())) {
                throw new IllegalStateException("启动异常：导出类型 [" + type.name() + "] 未找到对应 ExportExecutor 实现");
            }
        }
        this.executorByType = Collections.unmodifiableMap(byType);
    }

    /**
     * 按导出类型 key 取执行器（封闭域 fail-fast）
     *
     * <p>key 取自 {@link ExportTypeEnum#getKey()}，与 {@link ExportTask#getExportType()} 入库值同源；
     * 启动期已做过全覆盖校验，运行期抛出即意味着 key 未走枚举、是脏数据。
     *
     * @param typeKey 导出类型 key，不能为 null
     * @return 该类型对应的执行器
     * @throws IllegalArgumentException key 没有对应实现时抛出
     */
    public ExportExecutor<?, ?> getExecutor(String typeKey) {
        ExportExecutor<?, ?> executor = executorByType.get(typeKey);
        if (executor == null) {
            throw new IllegalArgumentException("未注册的导出类型: " + typeKey);
        }
        return executor;
    }

}
