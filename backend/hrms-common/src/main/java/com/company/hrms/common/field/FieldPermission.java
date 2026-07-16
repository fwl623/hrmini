package com.company.hrms.common.field;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 文档标记：本接口返回体含敏感字段，需要裁剪。
 * <p>
 * <b>不会自动生效</b>——Service 必须显式调用 {@link FieldPermissionFilter#filter}。
 * 空 AOP 已移除，避免「加注解就以为脱敏了」。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FieldPermission {
}
