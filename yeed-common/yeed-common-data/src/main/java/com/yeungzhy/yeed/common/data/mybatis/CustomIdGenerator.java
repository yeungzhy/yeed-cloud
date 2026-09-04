package com.yeungzhy.yeed.common.data.mybatis;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;

/**
 * 自定义 MyBatis-Plus 雪花 ID 生成器。
 *
 * <p>分布式高并发下，用默认构造器（固定或随机 workerId/dataCenterId）极易产生 ID 冲突。
 * 正确做法是为每个实例分配全局唯一且固定的 workerId/dataCenterId（0~31）。
 * 本类继承 {@link DefaultIdentifierGenerator} 复用其标准雪花算法，仅在构造时注入固定参数。
 *
 * <p>workerId/dataCenterId 来源二选一：
 * <ul>
 *   <li>静态配置：每个实例在 application.yml 显式指定
 *       {@code mybatis-plus.global-config.worker-id} / {@code datacenter-id}</li>
 *   <li>动态分配：Redis 自增原子操作分配，workerId 超 31 则 dataCenterId+1，
 *       组合出最多 32×32=1024 个独立实例</li>
 * </ul>
 *
 * @see com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator
 * @see DefaultIdentifierGenerator
 * @see <a href="https://github.com/baomidou/mybatis-plus/issues/6177#issuecomment-2213310846">
 *      Issue #6177：分布式环境下自定义 ID 生成器的最佳实践</a>
 * @author yeungzhy
 * @since 2026-08-02
 */
public class CustomIdGenerator extends DefaultIdentifierGenerator {

    /**
     * 构造自定义雪花 ID 生成器。
     *
     * @param workerId     机器 ID，0~31，必须全局唯一
     * @param dataCenterId 数据中心 ID，0~31，与 workerId 组合必须全局唯一
     */
    public CustomIdGenerator(long workerId, long dataCenterId) {
        super(workerId, dataCenterId);
    }

}
