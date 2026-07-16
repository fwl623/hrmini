package com.company.hrms.common.annotation;

import java.lang.annotation.*;

/**
 * 敏感字段加密注解
 *
 * 标记在需要 AES 加密存储的字段上，
 * 由 MyBatis-Plus 拦截器或 TypeHandler 自动处理加解密。
 * 支持：身份证号、银行卡号等敏感数据字段。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EncryptedField {
}
