package com.company.hrms.employee.auth;

import com.company.hrms.common.enums.RoleCode;
import com.company.hrms.common.exception.ForbiddenException;
import com.company.hrms.common.security.LoginUser;
import com.company.hrms.common.security.SecurityUtils;

/**
 * 员工档案管理端鉴权，与前端 {@code access.ts} / PRD §2.2 对齐：
 * <ul>
 *   <li>花名册读写：HR / 管理员 / 部门主管（EMPLOYEE 可读，靠 DataScope SELF）</li>
 *   <li>FINANCE / FINANCE_MANAGER：不可访问花名册与档案编辑（仅薪资域）</li>
 *   <li>薪资档案：HR / FINANCE / FINANCE_MANAGER；SYS_ADMIN 双拦截</li>
 * </ul>
 */
public final class EmployeeAccessGuard {

    public static final String PERM_MENU_EMPLOYEE = "menu:employee";

    private EmployeeAccessGuard() {
    }

    /**
     * 花名册列表/详情：禁止财务族；允许 HR/管理员/部门主管/员工（SELF 由 DataScope 收窄）。
     */
    public static void requireRosterRead() {
        LoginUser user = SecurityUtils.requireLoginUser();
        if (isFinanceFamily(user)) {
            throw new ForbiddenException();
        }
        if (isRosterPrivileged(user)
                || user.hasRole(RoleCode.EMPLOYEE.name())
                || user.hasPermission(PERM_MENU_EMPLOYEE)) {
            return;
        }
        throw new ForbiddenException();
    }

    /**
     * 花名册编辑 / 敏感字段 / 调岗历史：HR/管理员/部门主管；禁止财务族、纯 EMPLOYEE。
     */
    public static void requireRosterWrite() {
        LoginUser user = SecurityUtils.requireLoginUser();
        if (isFinanceFamily(user)) {
            throw new ForbiddenException();
        }
        if (isRosterPrivileged(user) || user.hasPermission(PERM_MENU_EMPLOYEE)) {
            return;
        }
        throw new ForbiddenException();
    }

    /**
     * 手机号变更 HR 待办/审批：仅 HR / 管理员。
     */
    public static void requireHrStaff() {
        LoginUser user = SecurityUtils.requireLoginUser();
        if (isHrOrAdmin(user)) {
            return;
        }
        throw new ForbiddenException();
    }

    /**
     * 员工薪资档案：HR / FINANCE / FINANCE_MANAGER；SYS_ADMIN 禁止。
     */
    public static void requireSalaryAccess() {
        LoginUser user = SecurityUtils.requireLoginUser();
        if (user.hasRole(RoleCode.SYS_ADMIN.name())) {
            throw new ForbiddenException();
        }
        if (user.hasRole(RoleCode.HR_STAFF.name()) || isFinanceFamily(user)) {
            return;
        }
        throw new ForbiddenException();
    }

    private static boolean isHrOrAdmin(LoginUser user) {
        return user.hasRole(RoleCode.SYS_ADMIN.name()) || user.hasRole(RoleCode.HR_STAFF.name());
    }

    private static boolean isRosterPrivileged(LoginUser user) {
        return isHrOrAdmin(user) || user.hasRole(RoleCode.DEPT_MANAGER.name());
    }

    /** 财务专员 + 财务经理（调岗调薪审批），均不可进花名册 */
    private static boolean isFinanceFamily(LoginUser user) {
        return user.hasRole(RoleCode.FINANCE.name())
                || user.hasRole(RoleCode.FINANCE_MANAGER.name());
    }
}
