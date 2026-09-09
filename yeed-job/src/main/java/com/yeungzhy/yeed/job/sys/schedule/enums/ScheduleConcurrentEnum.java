package com.yeungzhy.yeed.job.sys.schedule.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 定时计划并发策略（上次触发未结束时，本次触发如何处置）
 *
 * <p>三态对应的落地方式：Quartz 只原生支持后两者，{@link #SKIP} 须由执行壳自行实现
 * <ul>
 *   <li>{@link #SKIP}：Quartz 无原生等价能力，由执行壳抢分布式锁，抢不到即放弃本次</li>
 *   <li>{@link #ALLOW}：Quartz 默认行为，同一 JobKey 的多次触发互不干涉</li>
 *   <li>{@link #QUEUE}：由带 {@link org.quartz.DisallowConcurrentExecution} 的作业类承载，
 *       阻塞期内的触发在本次结束后补跑一次</li>
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-09-08
 */
@Getter
@AllArgsConstructor
public enum ScheduleConcurrentEnum implements IEnum<Integer> {

    /** 跳过（0）：上次未结束则放弃本次 */
    SKIP(0, "跳过"),

    /** 允许（1）：不做并发限制 */
    ALLOW(1, "允许"),

    /** 排队（2）：本次结束后补跑被阻塞的触发 */
    QUEUE(2, "排队"),

    ;

    @EnumValue
    @JsonValue
    private final Integer code;

    /** 中文描述（用于日志/字典渲染） */
    private final String desc;

    @Override
    public Integer getValue() {
        return this.code;
    }

    /**
     * 解析数据库值（Integer → 枚举）
     *
     * <p>封闭域语义：{@code null} 返回 {@code null}（支持"前端不传就不修改"），范围外取值视为脏数据 fail-fast
     *
     * @param code 数据库存储值（0/1/2）
     * @return 对应枚举；入参为 null 时返回 null
     * @throws IllegalArgumentException code 非法且非 null
     */
    public static ScheduleConcurrentEnum parse(Integer code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case 0 -> SKIP;
            case 1 -> ALLOW;
            case 2 -> QUEUE;
            default -> throw new IllegalArgumentException("未知的 ScheduleConcurrentEnum code: " + code);
        };
    }

}
