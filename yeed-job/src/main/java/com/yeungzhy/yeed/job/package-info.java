/**
 * yeed-job 模块包结构约定
 *
 * <p>本模块是独立部署的调度单元。被 {@code yeed_sys_schedule} 反射调用的目标 Bean 必须在本模块内，
 * 反射用的是本模块的 ApplicationContext，跨模块的类取不到
 *
 * <p>包结构：业务域优先，平台层集中于 sys 下
 * <pre>{@code
 * com.yeungzhy.yeed.job
 * ├── export/              导出业务域
 * │   ├── custom.package/  自定义所需包
 * │   └── schedule/        该域周期作业，类名以 Job 结尾
 * ├── order/               其他业务域同构，如 order、student
 * │   ├── client/          调其他模块的 Feign 客户端
 * │   └── schedule/        该域周期作业，类名以 Job 结尾
 * ├── sys/                 平台层：只放表 CRUD 与框架装配，不放业务
 * ├── bootstrap/           启动钩子
 * }</pre>
 *
 * <p>新增周期作业：在对应业务域下建 schedule 子包，类名以 {@code Job} 结尾，方法固定 public 且无参
 *
 * @author yeungzhy
 * @since 2026-09-08
 */
package com.yeungzhy.yeed.job;
