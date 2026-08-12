package com.yeungzhy.yeed.common.core.constant;

/**
 * 全局常量统一定义
 *
 * <p>跨模块共享的公共常量在此统一维护，禁止在业务代码中散落魔法字符串。
 * 日期时间格式与 {@code JacksonAutoConfiguration} 的全局序列化行为绑定，
 * 修改格式时需评估对历史数据与前端展示的影响。
 *
 * @author yeungzhy
 * @since 2026-08-01
 */
public class Constant {

    /** 日期时间格式：yyyy-MM-dd HH:mm:ss */
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    /** 日期格式：yyyy-MM-dd */
    public static final String DATE_PATTERN = "yyyy-MM-dd";
    /** 时间格式：HH:mm:ss */
    public static final String TIME_PATTERN = "HH:mm:ss";

}
