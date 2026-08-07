package com.yeungzhy.yeed.common.mybatis;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;

/**
 * 自定义 MyBatis-Plus 雪花算法 ID 生成器。
 * <p>
 * 在分布式高并发场景下，直接使用 {@code Sequence} 或 Hutool 的 {@code Snowflake} 默认构造器
 * （使用固定或随机 {@code workerId}/{@code dataCenterId}）极易产生 ID 冲突。
 * 正确的做法是实现 {@link com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator}，
 * 为每个应用实例分配 <b>全局唯一且固定</b> 的 {@code workerId} 和 {@code dataCenterId}（取值范围 0 ~ 31），
 * 从而确保雪花 ID 的唯一性。
 * </p>
 *
 * <p>
 * <b>推荐配置方式：</b><br>
 * 在 Spring Boot 的配置文件（如 {@code application.yml}）中为每个实例显式指定：
 * <pre>
 * mybatis-plus:
 *   global-config:
 *     worker-id: 1      # 实例 A 设为 1，实例 B 设为 2，以此类推
 *     datacenter-id: 1  # 实例 A 设为 1，实例 B 设为 1（可保持不变）
 * </pre>
 * 然后通过 {@code @ConfigurationProperties} 或 {@code @Value} 注入到本构造器中。
 * </p>
 *
 * <p>
 * <b>动态分配方案：</b><br>
 * 若实例数动态变化，也可使用 Redis 自增原子操作来分配：
 * 每次获取 {@code workerId} 时自动 +1，若 >31 则 {@code dataCenterId} + 1，
 * 组合出最多 32×32 = 1024 个独立实例。若机器数超过该上限，则需改造雪花算法（如扩展位长）。
 * </p>
 *
 * <p>
 * 本生成器继承自 MyBatis-Plus 的 {@link DefaultIdentifierGenerator}，
 * 完全复用了其标准雪花算法实现，只需在构造时注入固定的 {@code workerId} 和 {@code dataCenterId}。
 * </p>
 *
 * @see com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator
 * @see DefaultIdentifierGenerator
 * @see <a href="https://github.com/baomidou/mybatis-plus/issues/6177#issuecomment-2213310846">
 *      Issue #6177 讨论：分布式环境下自定义 ID 生成器的最佳实践</a>
 * @author yeungzhy
 * @since 2026-08-02
 */
public class CustomIdGenerator extends DefaultIdentifierGenerator {

    /**
     * 构造自定义雪花 ID 生成器。
     *
     * @param workerId     机器 ID，取值范围 0 ~ 31，必须全局唯一
     * @param dataCenterId 数据中心 ID，取值范围 0 ~ 31，与 workerId 组合必须全局唯一
     */
    public CustomIdGenerator(long workerId, long dataCenterId) {
        super(workerId, dataCenterId);
    }

}
