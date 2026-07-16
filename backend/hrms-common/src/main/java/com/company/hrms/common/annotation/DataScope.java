package com.company.hrms.common.annotation;

import java.lang.annotation.*;

/**
 * 数据权限注解
 * 由 DataScopeInterceptor 拦截处理，自动注入数据范围 SQL
 *
 * value: 数据范围类型
 *   AUTO       — 根据用户角色自动判定
 *   DEPT_TREE  — 本部门及下属
 *   SELF       — 仅本人
 *   ALL        — 全量
 * deptAlias: 部门表别名（用于 SQL 注入）
 * empAlias:  员工表别名（用于 SQL 注入）
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    String value() default "AUTO";
    String deptAlias() default "d";
    String empAlias() default "e";
}
