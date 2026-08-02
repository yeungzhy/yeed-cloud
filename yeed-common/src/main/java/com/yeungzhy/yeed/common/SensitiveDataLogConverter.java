package com.yeungzhy.yeed.common;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感数据脱敏转换器
 *
 * @author MaXueSong at 2023-12-14 08:48:04
 */
public final class SensitiveDataLogConverter extends MessageConverter {
    private static final String MASK_TWO_STARS = "**";
    private static final String MASK_FOUR_STARS = "****";
    private static final Logger log = LoggerFactory.getLogger(SensitiveDataLogConverter.class);

    /** 匹配结果：持有原始值与类型，使转换阶段无需再跑正则判定类型 */
    private record SensitiveData(String value, SensitiveDataType type) {}

    /** 敏感数据类型 */
    private enum SensitiveDataType {
        /** 手机号 */
        PHONE,
        /** 身份证 */
        ID_CARD,
        /** 邮箱 */
        EMAIL,
        /** 生日 */
        BIRTHDAY
    }

    /** 手机号正则匹配, 支持+86 0086 */
    private final static Pattern PHONE_PATTERN = Pattern.compile("((\\+|00)86)?1[3-9]\\d{9}");

    /** 身份证正则匹配, 支持15位和18位 */
    private final static Pattern ID_CARD_PATTERN = Pattern.compile("([1-9]\\d{5}(18|19|20)\\d{2}((0[1-9])|(10|11|12))(([0-2][1-9])|10|20|30|31)\\d{3}[0-9Xx])|([1-9]\\d{5}\\d{2}((0[1-9])|(10|11|12))(([0-2][1-9])|10|20|30|31)\\d{3})");

    /** 邮箱正则匹配 */
    private final static Pattern EMAIL_PATTERN = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    /** 生日正则匹配（YYYY-MM-DD YY-MM-DD DD-MM-YYYY格式，兼容符号【- / . _ 空格】） 目前支持年份1900-2099 */
    private final static Pattern BIRTHDAY_PATTERN = Pattern.compile("((?:19|20)\\d{2}[-._/\\s](?:0[1-9]|1[0-2])[-._/\\s](?:0[1-9]|[12][0-9]|3[01]))|((?:0[1-9]|[12][0-9]|3[01])[-._/\\s](?:0[1-9]|1[0-2])[-._/\\s](?:19|20)\\d{2})|(\\d{2}[-._/\\s](?:0[1-9]|1[0-2])[-._/\\s](?:0[1-9]|[12][0-9]|3[01]))");





    /**
     * 日志转换
     *
     * @param event 事件
     *
     * @return 脱敏后的日志信息
     */
    @Override
    public String convert(ILoggingEvent event) {
        try {
            // 获取日志打印信息
            String logMsg = event.getFormattedMessage();

            // 进行敏感数据匹配
            List<SensitiveData> list = validData(logMsg);

            if (!CollectionUtils.isEmpty(list)) {
                // 进行敏感数据替换
                for (SensitiveData sensitiveData : list) {
                    logMsg = convertDate(logMsg, sensitiveData);
                }
            }
            return logMsg;
        } catch (Exception e) {
            /*
             * 脱敏失败时，降级返回原文，确保业务日志不丢失，也不触发递归(异常日志再次触发 %seda 转换器)
             * 这个 logger 由于 additivity=false，且 pattern 不含 %seda，绝对不会触发递归
             */
            log.error("敏感数据日志转换器脱敏异常", e);
            return event.getFormattedMessage();
        }
    }


    /**
     * 正则匹配是否包含脱敏数据，并在匹配阶段同时确定数据类型
     *
     * @param param 日志文本
     * @return 匹配到的敏感数据（含类型）
     */
    private static List<SensitiveData> validData(String param) {
        List<SensitiveData> list = new ArrayList<>();
        // 匹配手机号
        Matcher phoneMatcher = PHONE_PATTERN.matcher(param);
        while (phoneMatcher.find()) {
            list.add(new SensitiveData(phoneMatcher.group(), SensitiveDataType.PHONE));
        }
        // 匹配身份证
        Matcher idCardMatcher = ID_CARD_PATTERN.matcher(param);
        while (idCardMatcher.find()) {
            list.add(new SensitiveData(idCardMatcher.group(), SensitiveDataType.ID_CARD));
        }
        // 匹配邮箱
        Matcher emailPatternMatcher = EMAIL_PATTERN.matcher(param);
        while (emailPatternMatcher.find()) {
            list.add(new SensitiveData(emailPatternMatcher.group(), SensitiveDataType.EMAIL));
        }
        // 匹配生日
        Matcher birthdayPatternMatcher = BIRTHDAY_PATTERN.matcher(param);
        while (birthdayPatternMatcher.find()) {
            list.add(new SensitiveData(birthdayPatternMatcher.group(), SensitiveDataType.BIRTHDAY));
        }
        return list;
    }


    /**
     * 数据脱敏：根据匹配阶段确定的类型直接处理，不再重复执行正则匹配
     *
     * @param logMsg 日志消息
     * @param data   敏感数据（值 + 类型）
     * @return 脱敏后的日志
     */
    private static String convertDate(String logMsg, SensitiveData data) {
        String param = data.value;
        String replaceContext;
        switch (data.type) {
            case PHONE:
                replaceContext = maskPhone(param);
                break;
            case ID_CARD:
                replaceContext = maskIdCard(param);
                break;
            case EMAIL:
                replaceContext = maskEmail(param);
                break;
            case BIRTHDAY:
                replaceContext = maskBirthday(param);
                break;
            default:
                return logMsg; // 未知类型，原样返回
        }
        return logMsg.replace(param, replaceContext);
    }


    /**
     * 手机号脱敏：保留前3位和后4位，中间4位用4个*替换；区号(+86 / 0086)不参与掩码
     * <pre>
     *   +8613912345678 → +86139****5678
     *   008613912345678 → 0086139****5678
     *   13912345678    → 139****5678
     * </pre>
     */
    private static String maskPhone(String param) {
        int phoneStart = 0;
        if (param.startsWith("+86")) {
            phoneStart = 3;
        } else if (param.startsWith("0086")) {
            phoneStart = 4;
        }
        String prefix = param.substring(0, phoneStart);
        String phone = param.substring(phoneStart);
        // 正则保证手机号本体为11位，此判断为兜底
        if (phone.length() < 11) {
            return param;
        }
        return prefix + phone.substring(0, 3) + MASK_FOUR_STARS + phone.substring(phone.length() - 4);
    }


    /**
     * 身份证脱敏：保留前6位（地区码）和后4位，中间用5个*替换
     * <pre>
     *   18位：110101199001011234 → 110101*****1234（中间8位 → 5个*）
     *   15位：110101900101123    → 110101*****1123（中间5位 → 5个*）
     * </pre>
     */
    private static String maskIdCard(String param) {
        int len = param.length();
        if (len == 18) {
            return param.substring(0, 6) + MASK_FOUR_STARS + param.substring(14);
        }
        if (len == 15) {
            return param.substring(0, 6) + MASK_FOUR_STARS + param.substring(11);
        }
        return param;
    }


    /**
     * 邮箱脱敏：保留用户名首字符和@后的域名，中间用5个*替换
     * <pre>
     *   john.doe@example.com → j*****@example.com
     * </pre>
     */
    private static String maskEmail(String param) {
        int atIndex = param.indexOf('@');
        if (atIndex <= 0) {
            return param;
        }
        String domain = param.substring(atIndex);
        return param.charAt(0) + MASK_FOUR_STARS + domain;
    }



    /**
     * 生日脱敏：保留年份和月份，隐藏日期（日段替换为 **）
     * <pre>
     *   1995-08-15 → 1995-08-**
     *   15-08-1995 → **-08-1995
     *   95-08-15   → 95-08-**
     *   1995/08.15 → 1995/08.**
     * </pre>
     * 思路：通过首末分隔符位置判断日段位置
     * <ul>
     *   <li>首段4位 → YYYY-MM-DD，日段在末尾</li>
     *   <li>末段4位 → DD-MM-YYYY，日段在开头</li>
     *   <li>否则视为 YY-MM-DD，日段在末尾</li>
     * </ul>
     */
    private static String maskBirthday(String param) {
        int firstSep = indexOfFirstSeparator(param);
        int lastSep = indexOfLastSeparator(param, firstSep);
        if (firstSep < 0 || lastSep < 0 || firstSep == lastSep) {
            return param;
        }
        int lastPartLen = param.length() - lastSep - 1;
        if (firstSep == 4) {
            // YYYY-MM-DD
            return param.substring(0, lastSep + 1) + MASK_TWO_STARS;
        } else if (lastPartLen == 4) {
            // DD-MM-YYYY
            return MASK_TWO_STARS + param.substring(firstSep);
        } else {
            // YY-MM-DD
            return param.substring(0, lastSep + 1) + MASK_TWO_STARS;
        }
    }


    /**
     * 查找首个分隔符位置（- / . _ 空格）
     */
    private static int indexOfFirstSeparator(String param) {
        for (int i = 0, len = param.length(); i < len; i++) {
            char c = param.charAt(i);
            if (isSeparator(c)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 查找末个分隔符位置（从首个分隔符之后开始向后扫描，保证存在两个分隔符）
     */
    private static int indexOfLastSeparator(String param, int startFrom) {
        int last = -1;
        for (int i = param.length() - 1; i > startFrom; i--) {
            if (isSeparator(param.charAt(i))) {
                return i;
            }
        }
        return last;
    }

    /**
     * 是否为生日分隔符
     */
    private static boolean isSeparator(char c) {
        return c == '-' || c == '.' || c == '_' || c == '/' || Character.isWhitespace(c);
    }


}
