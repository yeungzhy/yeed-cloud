package com.yeungzhy.yeed.common.data.mybatis;

import lombok.Data;

/**
 * 雪花 ID 的 workerId/dataCenterId 配置属性
 *
 * <p> 绑定 {@code mybatis-plus.global-config.worker-id} / {@code data-center-id}，
 * 见 {@link com.yeungzhy.yeed.common.data.config.MybatisPlusAutoConfiguration#customIdProperties()}
 *
 * @author yeungzhy
 * @since 2026-08-02
 */
@Data
public class CustomIdProperties {

    /**
     * 机器 ID，0~31，必须全局唯一
     *
     * <p> 必填：字段是包装类型，配置缺失时拆箱会抛 NullPointerException 并中断启动
     */
    private Long workerId;

    /**
     * 数据中心 ID，0~31，与 workerId 组合必须全局唯一
     *
     * <p> 必填：同 {@link #workerId}，配置缺失会中断启动
     */
    private Long dataCenterId;

}
