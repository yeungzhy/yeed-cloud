package com.yeungzhy.yeed.common.core.constant;

import java.time.format.DateTimeFormatter;

/**
 * 全局常量统一定义
 *
 * <p> 跨模块共享的公共常量在此统一维护，禁止在业务代码中散落魔法字符串
 * 日期时间格式与 {@code JacksonAutoConfiguration} 的全局序列化行为绑定，
 * 修改格式时需评估对历史数据与前端展示的影响
 *
 * @author yeungzhy
 * @since 2026-08-01
 */
public class Constant {

    /** 请求头令牌标识 */
    public static final String TOKEN_HEADER = "Authorization";

    /**
     * 无用户 ID 哨兵值：非用户操作（系统引导数据、定时任务等）填充 createBy/updateBy 用
     *
     * <p> 雪花 ID 永不生成 0，与真实用户 ID 天然不冲突；该值不代表任何用户，关联查询必然查不到，
     * 展示层需特判为「系统」
     * <p> 审计字段由 AutoFillFieldHandler 按登录态自动填充，非用户操作场景须显式声明，避免被误判为身份透传失败
     */
    public static final Long NO_USER_ID = 0L;

    /** 日期时间格式：yyyy-MM-dd HH:mm:ss */
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
    /** 日期格式：yyyy-MM-dd */
    public static final String DATE_PATTERN = "yyyy-MM-dd";
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_PATTERN);
    /** 时间格式：HH:mm:ss */
    public static final String TIME_PATTERN = "HH:mm:ss";
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(TIME_PATTERN);
    /** 紧凑时间戳格式：yyyyMMddHHmmssSSS（无分隔符，用于文件名/批次号等） */
    public static final String COMPACT_DATE_TIME_PATTERN = "yyyyMMddHHmmssSSS";
    public static final DateTimeFormatter COMPACT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(COMPACT_DATE_TIME_PATTERN);

}
