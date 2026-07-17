package com.company.hrms.common.datascope;

import com.company.hrms.common.enums.DataScopeType;
import com.company.hrms.common.security.LoginUser;

/**
 * 按数据范围拼装安全的 SQL 片段（仅拼接已校验的数字 ID，禁止用户输入）。
 */
public final class DataScopeSqlBuilder {

    private DataScopeSqlBuilder() {
    }

    public static String build(DataScopeType scope, LoginUser user, DataScope ann) {
        if (scope == null || user == null) {
            return " AND 1=0";
        }
        String empAlias = ann.empAlias();
        String empColumn = ann.empColumn();
        String deptColumn = ann.deptColumn();

        return switch (scope) {
            case ALL, PAYROLL, NONE_PAYROLL -> "";
            case DEPT_TREE -> buildDeptTree(user.getDeptId(), empAlias, deptColumn);
            case SELF -> buildSelf(user.getEmployeeId(), empAlias, empColumn);
            case AUTO -> " AND 1=0";
        };
    }

    private static String buildSelf(Long employeeId, String empAlias, String empColumn) {
        if (employeeId == null || employeeId <= 0) {
            return " AND 1=0";
        }
        String col = qualify(empAlias, empColumn);
        if (col == null) {
            return " AND 1=0";
        }
        return " AND " + col + " = " + employeeId;
    }

    private static String buildDeptTree(Long deptId, String empAlias, String deptColumn) {
        if (deptId == null || deptId <= 0) {
            return " AND 1=0";
        }
        String col = qualify(empAlias, deptColumn);
        if (col == null) {
            return " AND 1=0";
        }
        // path 子树；deptId 已为数字，无注入风险
        return " AND " + col + " IN ("
                + "SELECT id FROM department WHERE deleted = 0 "
                + "AND path LIKE CONCAT((SELECT path FROM department WHERE id = " + deptId + "), '%')"
                + ")";
    }

    /** @return 安全限定名，非法标识符返回 null */
    private static String qualify(String alias, String column) {
        if (!isSafeSqlIdent(column)) {
            return null;
        }
        if (alias == null || alias.isBlank()) {
            return column;
        }
        if (!isSafeSqlIdent(alias)) {
            return null;
        }
        return alias + "." + column;
    }

    private static boolean isSafeSqlIdent(String ident) {
        return ident != null && ident.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }
}
