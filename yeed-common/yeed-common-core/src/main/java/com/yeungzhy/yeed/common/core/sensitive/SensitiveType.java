package com.yeungzhy.yeed.common.core.sensitive;

import java.util.regex.Pattern;

/**
 * 敏感数据类型及掩码策略
 *
 * <p>掩码规则由各常量自实现：新增敏感类型只需追加一个常量并实现 {@link #mask(String)}，
 * VO 字段标注 {@link Sensitive} 即刻生效，无需改动其他类。
 *
 * <p>同一份类型定义服务两条链路，区别是“敏感串从哪来”：
 * <ul>
 *   <li>VO 出参：字段标 {@link Sensitive}，由 {@link SensitiveJsonSerializer} 调 {@link #mask(String)}。
 *       值本身就是敏感串，掩码保留部分字符便于界面辨识；
 *   <li>自由文本：由 {@link SensitiveTextUtil} 用 {@link #discoveryPattern()} 从文本中间捞出片段再打码。
 *       文本里可能有多个、也可能夹在长句中，只能按值发现。
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-08-07
 * @see Sensitive
 * @see SensitiveTextUtil
 */
public enum SensitiveType {

    /**
     * 邮箱：保留首字符 + {@code ****} + {@code @} + 域名
     * <pre>{@code john.doe@example.com → j****@example.com}</pre>
     */
    EMAIL {
        @Override
        public String mask(String value) {
            if (value == null || value.isEmpty()) return value;
            int at = value.indexOf('@');
            // 无 @ 或本地部分为空时原样返回，避免掩码后语义丢失
            if (at <= 0) return value;
            return value.charAt(0) + "****" + value.substring(at);
        }

        /**
         * 本地部分（含 {@code . _ % + -}）+ {@code @} + 至少两级的域名
         * <p>字符类刻意不含引号与空白，否则会越过 JSON 字符串的边界匹配
         */
        @Override
        public Pattern discoveryPattern() {
            return Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
        }
    },

    /**
     * 手机号：前 3 + {@code ****} + 后 4
     * <pre>{@code 13912345678 → 139****5678}</pre>
     */
    PHONE {
        /** 国际区号：长度不同，需先剥离再对 11 位本体掩码；顺序影响匹配，长前缀在前 */
        private static final String[] COUNTRY_CODES = {"+86", "0086", "86"};

        @Override
        public String mask(String value) {
            if (value == null || value.isEmpty()) return value;
            // 区号不参与掩码，否则 "+86" 会被算进前 3 位，产出 "+86****5678"
            int offset = 0;
            for (String code : COUNTRY_CODES) {
                if (value.startsWith(code)) {
                    offset = code.length();
                    break;
                }
            }
            String body = value.substring(offset);
            // 长度不足 7 位时掩码后无可辨识信息，原样返回（兜底，正则保证手机号本体 11 位）
            if (body.length() < 7) return value;
            return value.substring(0, offset) + body.substring(0, 3) + "****" + body.substring(body.length() - 4);
        }

        /**
         * 11 位本机号码（首位 1、次位 3-9），可带 {@code +86} / {@code 0086} / {@code 86} 区号
         * <p>两侧的数字边界不可省：雪花 ID、毫秒时间戳这类长数字串里极易出现形态吻合的 11 位片段，
         * 实测 19 位雪花 ID 被打成 {@code 1***7074316}、13 位时间戳被吞成 {@code ***89}。
         * <p>左侧边界含 {@code +}：否则 {@code +8613912345678} 只匹配 {@code 86139...} 部分，
         * 把 {@code +} 留在结果里变成 {@code 手机 86***}。
         */
        @Override
        public Pattern discoveryPattern() {
            return Pattern.compile("(?<![0-9+])(?:\\+86|0086|86)?1[3-9]\\d{9}(?![0-9])");
        }
    },

    /**
     * 身份证：前 6（地区码）+ {@code ****} + 后 4
     * <pre>{@code
     * 18 位 110101199001011234 → 110101****1234
     * 15 位 110101900101123    → 110101****1123
     * }</pre>
     */
    ID_CARD {
        @Override
        public String mask(String value) {
            if (value == null || value.isEmpty()) return value;
            int len = value.length();
            if (len == 18) return value.substring(0, 6) + "****" + value.substring(14);
            if (len == 15) return value.substring(0, 6) + "****" + value.substring(11);
            return value;
        }

        /**
         * 18 位（6 地区码 + 8 出生日期 + 3 顺序码 + 1 校验位，可为 X）与 15 位（无校验位）
         * <p>日期段按合法区间收紧（月份 01-12、日 01-31），既提升精度也降低误伤；
         * 两侧数字边界同 {@link #PHONE}，避免长数字串中段被误判
         */
        @Override
        public Pattern discoveryPattern() {
            return Pattern.compile("(?<![0-9])(?:"
                    + "[1-9]\\d{5}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]"
                    + "|[1-9]\\d{7}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}"
                    + ")(?![0-9])");
        }
    },

    /**
     * 凭据：密码、令牌、密钥、验证码等
     * <pre>{@code Abc@123456 → ***}</pre>
     * <p>整段打码、不保留前后缀，与手机号等类型刻意不同：掩码残留对凭据没有辨识价值
     * （{@code A****6} 既不能定位也不能核对），却实打实缩小了暴力破解的搜索空间
     */
    CREDENTIAL {
        @Override
        public String mask(String value) {
            return value == null || value.isEmpty() ? value : "***";
        }

        /**
         * 凭据没有发现正则，返回 null
         * <p>凭据是没有任何格式特征的一类数据——密码、JWT、API 密钥长得千差万别，无法从文本里认出，只能靠字段名指明
         */
        @Override
        public Pattern discoveryPattern() {
            return null;
        }
    },

    /**
     * 用户名：首 1 字符 + {@code ****} + 末 1 字符
     * <pre>{@code
     * zhangsan → z****n
     * ab       → a*
     * }</pre>
     */
    USERNAME {
        @Override
        public String mask(String value) {
            if (value == null || value.isEmpty()) return value;
            int len = value.length();
            if (len == 1) return value;
            if (len == 2) return value.charAt(0) + "*";
            return value.charAt(0) + "****" + value.charAt(len - 1);
        }

        /**
         * 用户名没有发现正则，返回 null
         * <p>用户名没有可识别的格式特征，任意字符串都可能是用户名，只能靠字段名指明
         */
        @Override
        public Pattern discoveryPattern() {
            return null;
        }
    };

    /**
     * 对明文执行掩码
     *
     * @param value 明文
     * @return 掩码后的字符串
     */
    public abstract String mask(String value);

    /**
     * 从自由文本中认出本类型的正则；无格式特征、无法按值识别的类型返回 null
     *
     * @return 发现正则
     */
    public abstract Pattern discoveryPattern();

}
