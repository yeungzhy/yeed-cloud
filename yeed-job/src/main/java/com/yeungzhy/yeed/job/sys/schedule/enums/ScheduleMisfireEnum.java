package com.yeungzhy.yeed.job.sys.schedule.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错过触发补偿策略（服务停机、线程池占满导致该触发的时刻没触发）
 *
 * <p>常见误解是"补跑"等于"把错过的每一次都补上"：{@link #FIRE_ONCE} 只补跑一次，与错过多久、
 * 错过几次无关；Quartz 的 CronTrigger 未提供"补跑全部"，想全量补偿只能让业务方法按"上次成功时间"回溯处理
 *
 * @author yeungzhy
 * @since 2026-09-08
 */
@Getter
@AllArgsConstructor
public enum ScheduleMisfireEnum implements IEnum<Integer> {

    /** 丢弃（0）：重启后跳过错过的触发，从下一个周期继续 */
    IGNORE(0, "丢弃"),

    /** 补跑一次（1）：重启后立即触发一次，随后回到正常周期 */
    FIRE_ONCE(1, "补跑一次"),

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
     * @param code 数据库存储值（0/1）
     * @return 对应枚举；入参为 null 时返回 null
     * @throws IllegalArgumentException code 非法且非 null
     */
    public static ScheduleMisfireEnum parse(Integer code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case 0 -> IGNORE;
            case 1 -> FIRE_ONCE;
            default -> throw new IllegalArgumentException("未知的 ScheduleMisfireEnum code: " + code);
        };
    }

}
