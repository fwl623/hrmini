package com.company.hrms.module.org.service;

import com.company.hrms.common.enums.RoleCode;
import com.company.hrms.common.exception.ForbiddenException;
import com.company.hrms.common.security.LoginUser;
import com.company.hrms.common.security.SecurityUtils;

/**
 * 组织架构接口鉴权，与前端 {@code access.ts} 对齐：
 * <ul>
 *   <li>写操作：SYS_ADMIN / HR_STAFF，或持有对应 edit 权限码</li>
 *   <li>读操作：SYS_ADMIN / HR_STAFF / DEPT_MANAGER，或持有 view/edit/menu 权限</li>
 *   <li>FINANCE / 纯 EMPLOYEE：不可读组织架构（财务仅薪资域）</li>
 * </ul>
 */
public final class OrgAccessGuard {

    public static final String PERM_DEPT_VIEW = "org:dept:view";
    public static final String PERM_DEPT_EDIT = "org:dept:edit";
    public static final String PERM_POSITION_VIEW = "org:position:view";
    public static final String PERM_POSITION_EDIT = "org:position:edit";
    public static final String PERM_MENU_ORG = "menu:org";

    private OrgAccessGuard() {
    }

    /** @deprecated 使用 {@link #requireDeptWrite()} / {@link #requireDeptRead()} */
    @Deprecated
    public static void requireOrgAdmin() {
        requireDeptWrite();
    }

    public static void requireDeptRead() {
        LoginUser user = SecurityUtils.requireLoginUser();
        if (isOrgPrivilegedRole(user)
                || user.hasPermission(PERM_DEPT_VIEW)
                || user.hasPermission(PERM_DEPT_EDIT)
                || user.hasPermission(PERM_MENU_ORG)) {
            return;
        }
        throw new ForbiddenException();
    }

    public static void requireDeptWrite() {
        LoginUser user = SecurityUtils.requireLoginUser();
        if (isHrOrAdmin(user) || user.hasPermission(PERM_DEPT_EDIT)) {
            return;
        }
        throw new ForbiddenException();
    }

    public static void requirePositionRead() {
        LoginUser user = SecurityUtils.requireLoginUser();
        if (isOrgPrivilegedRole(user)
                || user.hasPermission(PERM_POSITION_VIEW)
                || user.hasPermission(PERM_POSITION_EDIT)
                || user.hasPermission(PERM_MENU_ORG)) {
            return;
        }
        throw new ForbiddenException();
    }

    public static void requirePositionWrite() {
        LoginUser user = SecurityUtils.requireLoginUser();
        if (isHrOrAdmin(user) || user.hasPermission(PERM_POSITION_EDIT)) {
            return;
        }
        throw new ForbiddenException();
    }

    private static boolean isHrOrAdmin(LoginUser user) {
        return user.hasRole(RoleCode.SYS_ADMIN.name()) || user.hasRole(RoleCode.HR_STAFF.name());
    }

    /** 可选部门/职位的管理端角色（不含 FINANCE / 纯 EMPLOYEE） */
    private static boolean isOrgPrivilegedRole(LoginUser user) {
        return isHrOrAdmin(user) || user.hasRole(RoleCode.DEPT_MANAGER.name());
    }
}
