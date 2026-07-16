package com.company.hrms.common.enums;

/**
 * 数据范围（PRD §2.1 / sys_role.data_scope）。
 * <p>
 * 角色落库只用：ALL / DEPT_TREE / SELF / PAYROLL / NONE_PAYROLL。
 * AUTO 仅用于 {@code @DataScope} 注解默认值，表示跟随当前用户角色的 data_scope。
 */
public enum DataScopeType {

    /** 全部数据 */
    ALL,
    /** 本部门及下级（path 子树） */
    DEPT_TREE,
    /** 仅本人 */
    SELF,
    /** 薪资相关全量（HR/财务） */
    PAYROLL,
    /** 非薪资全量；薪资接口须额外双拦截（SYS_ADMIN） */
    NONE_PAYROLL,
    /** 注解默认：按 LoginUser.dataScope 解析，不落库 */
    AUTO;

    public static DataScopeType from(String value) {
        if (value == null || value.isBlank()) {
            return SELF;
        }
        String normalized = value.trim().toUpperCase();
        // 兼容系分历史写法 DEPT
        if ("DEPT".equals(normalized)) {
            return DEPT_TREE;
        }
        return DataScopeType.valueOf(normalized);
    }

    /** 是否为角色可持久化的范围（排除 AUTO）。 */
    public boolean isPersistent() {
        return this != AUTO;
    }
}
