package com.yeungzhy.yeed.api.export;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 异步导出任务类型：admin 与 job 共享的业务类型契约
 *
 * <p>{@link #getKey()} 是类型在链路上的唯一标识，同时承担两种身份：admin 侧作为
 * {@link com.yeungzhy.yeed.api.export.task.dto.ExportTaskSaveDTO} 的 {@code taskType} 经 Feign 传入；
 * job 侧由 {@code ExportExecutorRegistry} 按它建执行器路由表，任务表落库的也是它。
 * 取稳定字符串而非序号：日志、配置、DB 里都能直接读出业务含义，也不会因常量顺序调整而漂移。
 *
 * <p>新增常量必须同步补两处，否则后果明确：
 * <ul>
 *   <li>job 补一个标注 {@code @Component} 的 {@code ExportExecutor} 实现；</li>
 *   <li>admin 在业务入口装配该类型。</li>
 * </ul>
 *
 * <p>同步导出不经过本枚举：它在业务模块内联完成，不进 job 异步链路，故无需对称预留「同步」类型。
 *
 * @author yeungzhy
 * @since 2026-08-14
 */
@Getter
@AllArgsConstructor
public enum ExportTaskTypeEnum {



    ;

    /**
     * 类型标识：job 执行器的注册键，也是任务表导出类型列的落库值
     *
     * <p>一旦有任务落库即成为历史数据，不得改名——改了存量任务就路由不到执行器。
     */
    private final String key;

}
