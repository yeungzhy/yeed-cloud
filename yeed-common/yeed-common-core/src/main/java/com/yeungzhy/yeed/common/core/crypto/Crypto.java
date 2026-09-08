package com.yeungzhy.yeed.common.core.crypto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 实体字段加解密注解
 *
 * <p> 标注在实体字段上，由 common-data 的 {@code FieldCryptoInterceptor} 在
 * MyBatis 入库前加密、出库后解密，支持嵌套 POJO / Map / Collection 递归处理
 *
 * @author YangZhaoHuang
 * @since 2026-05-22
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Crypto {

}
