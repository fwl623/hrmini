package com.company.hrms.common.datascope;

import com.company.hrms.common.enums.DataScopeType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注需要行级数据权限过滤的查询方法。
 * <p>
 * 用法示例：
 * <ul>
 *   <li>{@code @DataScope} / {@code @DataScope(AUTO)} — 按当前用户角色 data_scope 过滤</li>
 *   <li>{@code @DataScope(SELF)} — 强制仅本人（如 /profile/*）</li>
 *   <li>{@code @DataScope(DEPT_TREE)} — 强制本部门及下级</li>
 * </ul>
 * 拦截器实现将在对接员工列表等查询时完善。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /**
     * 本接口要求的数据范围。
     * AUTO：跟随 LoginUser.dataScope；其余为强制覆盖（取更严一侧由拦截器实现）。
     */
    DataScopeType value() default DataScopeType.AUTO;

    /** 部门表别名，如 d */
    String deptAlias() default "d";

    /** 员工表别名，如 e */
    String empAlias() default "e";

    /** 部门 ID 列名（不含表别名），默认 department_id */
    String deptColumn() default "department_id";

    /** 员工 ID 列名（不含表别名），默认 id */
    String empColumn() default "id";
}
