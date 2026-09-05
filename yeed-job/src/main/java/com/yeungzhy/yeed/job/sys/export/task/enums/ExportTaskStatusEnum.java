package com.yeungzhy.yeed.job.sys.export.task.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 异步导出任务状态枚举（任务生命周期唯一真相源）
 * <p>覆盖导出任务的完整生命周期流转：
 * <ul>
 *   <li>WAITING（0-待执行）：任务创建后、执行器认领前</li>
 *   <li>RUNNING（1-执行中）：执行器认领后，分批写 Excel/上传 OSS 期间，{@code progress} 分批推进</li>
 *   <li>SUCCESS（2-成功）：文件已上传 OSS，{@code ossId}/{@code fileSize} 已回写</li>
 *   <li>FAILED（3-失败）：执行异常，{@code failReason} 记录原因，可进入重试链路</li>
 * </ul>
 * <p>DB 层经 {@link EnumValue} 与 TINYINT 互转；对外接口契约稳定为整数（{@link JsonValue} 双向）。
 * <p>禁止在业务代码中散落裸数字 0/1/2/3 判断任务状态，一律使用枚举常量或 {@link #parse(Integer)}。
 *
 * @author yeungzhy
 * @since 2026-08-22
 */
@Getter
@AllArgsConstructor
public enum ExportTaskStatusEnum implements IEnum<Integer> {

    /** 待执行（0） */
    WAITING(0, "待执行"),

    /** 执行中（1） */
    RUNNING(1, "执行中"),

    /** 成功（2） */
    SUCCESS(2, "成功"),

    /** 失败（3） */
    FAILED(3, "失败"),

    ;

    /** 数据库存储值（0-待执行，1-执行中，2-成功，3-失败） */
    @EnumValue
    @JsonValue
    private final Integer code;

    /** 中文描述（用于日志/字典渲染） */
    private final String desc;

    /** 实现 {@link IEnum#getValue()}，返回 MP 写入数据库的值 */
    @Override
    public Integer getValue() {
        return this.code;
    }

    /**
     * 解析数据库值（DTO/前端入参的 Integer → 枚举）
     * <p>封闭域解析语义：{@code null} 入参返回 {@code null}（便于"前端不传就不修改"）；
     * 范围外取值视为脏数据，抛出 {@link IllegalArgumentException} fail-fast 暴露。
     *
     * @param code 数据库存储值（0/1/2/3）
     * @return 对应枚举；入参为 null 时返回 null
     * @throws IllegalArgumentException code 非法且非 null
     */
    public static ExportTaskStatusEnum parse(Integer code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case 0 -> WAITING;
            case 1 -> RUNNING;
            case 2 -> SUCCESS;
            case 3 -> FAILED;
            default -> throw new IllegalArgumentException("未知的 ExportTaskStatusEnum code: " + code);
        };
    }

    /** 是否"待执行"状态（空值视为非待执行，便于防御式判断） */
    public static boolean isWaiting(ExportTaskStatusEnum status) {
        return status == WAITING;
    }

    /** 是否"执行中"状态（空值视为非执行中，便于防御式判断） */
    public static boolean isRunning(ExportTaskStatusEnum status) {
        return status == RUNNING;
    }

    /** 是否"成功"状态（空值视为非成功，便于防御式判断） */
    public static boolean isSuccess(ExportTaskStatusEnum status) {
        return status == SUCCESS;
    }

    /** 是否"失败"状态（空值视为非失败，便于防御式判断） */
    public static boolean isFailed(ExportTaskStatusEnum status) {
        return status == FAILED;
    }

}
