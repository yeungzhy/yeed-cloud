package com.yeungzhy.yeed.common.core.sensitive;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 日志敏感数据脱敏转换器（logback）
 *
 * <p>生效方式：把转换符 {@code seda} 指向本类，再把日志 pattern 里的 {@code %m} 换成 {@code %seda}：
 * <pre>{@code
 * <conversionRule conversionWord="seda"
 *     class="com.yeungzhy.yeed.common.core.sensitive.SensitiveDataLogConverter"/>
 * }</pre>
 * dev 环境可把 {@code seda} 指向原生 {@link MessageConverter}（本地不脱敏，排查时可见原文）。
 *
 * <p>脱敏委托 {@link SensitiveTextUtil}（按值打码手机号、身份证、邮箱）与 {@link SensitiveJsonUtil}
 * （按键名整值替换），识别与掩码规则全部取自 {@link SensitiveType}，日志与报文两条链路同步生效
 *
 * <p>递归防护内建于本类：脱敏工具自身打印的日志按 logger 名直接透传、不再二次脱敏
 *
 * <p>覆盖边界：只认键名与有格式特征的值
 *
 * @author yeungzhy
 * @since 2026-08-06
 * @see Sensitive
 * @see SensitiveTextUtil
 * @see SensitiveType#discoveryPattern()
 */
public class SensitiveDataLogConverter extends MessageConverter {
    private static final Logger log = LoggerFactory.getLogger(SensitiveDataLogConverter.class);

    /**
     * 脱敏工具自身的 logger：命中直接透传、不再脱敏
     *
     * <p>递归链：业务日志 → 本转换器 → 脱敏工具 {@link SensitiveJsonUtil#mask(String)} 解析失败 → 工具打日志
     * → 该日志再次经过本转换器 → 若不拦截将再次委托脱敏工具 → 对非 JSON 文本再失败再告警，无限递归
     *
     * <p>故脱敏工具的告警必须原样放行。集合须包含所有会打日志的脱敏工具，新增时同步补录。
     */
    private static final Set<String> MASK_BYPASS_LOGGERS = Set.of(
            SensitiveJsonUtil.class.getName(),
            SensitiveTextUtil.class.getName(),
            SensitiveDataLogConverter.class.getName());

    /**
     * 输出前脱敏日志消息
     *
     * @return 脱敏后的消息；脱敏工具自身日志透传原文
     */
    @Override
    public String convert(ILoggingEvent event) {
        // 脱敏工具告警透传：再次脱敏既不会改变结果，还会把"解析失败"无限重放
        if (MASK_BYPASS_LOGGERS.contains(event.getLoggerName())) {
            return event.getFormattedMessage();
        }

        try {
            // 顺序不可颠倒：先按键名把无格式特征的数据掩码, 再匹配文本中有格式特征的数据进行脱敏
            return SensitiveTextUtil.mask(SensitiveJsonUtil.mask(event.getFormattedMessage()));
        } catch (Exception e) {
            /*
             * 降级返回原文，避免脱敏异常打断整条日志输出。
             * 工具解析失败被工具自己消化、不会抛到此处，此分支只兜住实现缺陷；
             * 告警本身已在上方按 logger 名放行，不会再触发脱敏。
             */
            log.error("Cannot mask sensitive data in log message, the raw message is returned as-is", e);
            return event.getFormattedMessage();
        }
    }

}
