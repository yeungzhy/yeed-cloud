package com.yeungzhy.yeed.common.data.mybatis;

import lombok.Data;

/**
 * 雪花 ID 的 workerId/dataCenterId 配置属性。
 *
 * <p>绑定 {@code yeed.mybatis.worker-id} / {@code yeed.mybatis.datacenter-id} 配置，
 * 供 {@link CustomIdGenerator} 构造时注入；仅用于单体/少量实例的静态分配场景。
 *
 * @author yeungzhy
 * @since 2026-08-02
 */
@Data
public class CustomIdProperties {

    /** 机器 ID，0~31，全局唯一 */
    private Long workerId;

    /** 数据中心 ID，0~31，与 workerId 组合全局唯一 */
    private Long dataCenterId;

}
