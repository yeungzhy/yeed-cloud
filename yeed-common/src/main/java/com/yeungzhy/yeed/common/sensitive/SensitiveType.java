package com.yeungzhy.yeed.common.sensitive;

/**
 * 敏感数据类型及掩码策略
 *
 * <p> 每种类型自含 {@link #mask(String)} 掩码实现，强内聚：
 * 新增敏感类型只需在此枚举追加一个值并实现 mask 方法，VO 字段标注 {@link Sensitive} 即生效。
 *
 * <p> 掩码风格与 {@link com.yeungzhy.yeed.common.support.SensitiveDataLogConverter}（日志正则脱敏）保持一致，
 * 但两者职责不重叠：本枚举面向 HTTP 响应 VO 字段精确掩码，日志转换器面向日志消息全文正则匹配。
 *
 * @author yeungzhy
 * @since 2026-08-07
 */
public enum SensitiveType {


    /**
     * 邮箱：保留首字符 + {@code ****} + @{@code 域名}
     * <pre> john.doe@example.com → j****@example.com </pre>
     */
    EMAIL {
        @Override
        public String mask(String value) {
            if (value == null || value.isEmpty()) return value;
            int at = value.indexOf('@');
            // 无 @ 或仅 @（本地部分为空）时原样返回，避免掩码后语义丢失
            if (at <= 0) return value;
            return value.charAt(0) + "****" + value.substring(at);
        }
    },


    /**
     * 手机号：前3 + {@code ****} + 后4
     * <pre> 13912345678 → 139****5678 </pre>
     */
    PHONE {
        @Override
        public String mask(String value) {
            if (value == null || value.isEmpty()) return value;
            // 长度不足 7 位时掩码后无可辨识信息，原样返回（兜底，正则保证手机号本体 11 位）
            if (value.length() < 7) return value;
            return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
        }
    },


    /**
     * 身份证：前6（地区码）+ {@code ****} + 后4
     * <pre> 18位：110101199001011234 → 110101****1234 </pre>
     * <pre> 15位：110101900101123    → 110101****1123 </pre>
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
    },


    /**
     * 用户名：首1字符 + {@code ****} + 末1字符
     * <pre> zhangsan → z****n </pre>
     * <pre> ab       → a*      </pre>
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
    };





    /**
     * 对明文执行掩码
     *
     * <p> 实现必须 null/空安全：入参为 null 或空串时原样返回。
     * 掩码过程若发生异常应降级返回 null（避免单字段脱敏失败拖垮整次 JSON 序列化），
     * 各实现内部不再 try-catch，由 {@link SensitiveJsonSerializer} 统一兜底。
     *
     * @param value 明文（调用方保证入参已是解密后的明文）
     * @return 掩码后的字符串
     */
    public abstract String mask(String value);

}
