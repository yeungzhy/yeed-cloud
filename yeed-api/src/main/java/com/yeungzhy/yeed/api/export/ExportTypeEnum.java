package com.yeungzhy.yeed.api.export;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 异步导出任务类型：admin 与 job 共享的业务类型契约
 *
 * <p>{@link #getKey()} 贯穿全链路：admin 侧作为
 * {@link com.yeungzhy.yeed.api.export.task.dto.ExportTaskSaveDTO} 的 {@code exportType} 经 Feign 传入，
 * job 侧由 {@code ExporterRegistry} 按它路由执行器，任务表落库的也是它
 *
 * <p>取稳定字符串而非序号：日志、配置、DB 里都能直接读出业务含义，也不会因常量顺序调整而漂移
 *
 * <p>新增常量须同步补两处，否则任务建得出来却路由不到执行器
 * <ul>
 *   <li>job：补一个标注 {@code @Component} 的导出执行器实现
 *   <li>admin：在业务入口装配该类型
 * </ul>
 *
 * <p>同步导出不经过本枚举：它在业务模块内联完成，不进 job 异步链路
 *
 * @author yeungzhy
 * @since 2026-08-14
 */
@Getter
@AllArgsConstructor
public enum ExportTypeEnum {

    /** 用户列表导出 */
    USER_EXPORT("user_export"),

    ;

    /** 类型标识：job 执行器注册键与任务表落库值 */
    private final String key;

}
