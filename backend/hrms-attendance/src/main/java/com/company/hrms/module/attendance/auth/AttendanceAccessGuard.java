package com.company.hrms.module.attendance.auth;

import com.company.hrms.common.enums.RoleCode;
import com.company.hrms.common.exception.ForbiddenException;
import com.company.hrms.common.security.LoginUser;
import com.company.hrms.common.security.SecurityUtils;

/**
 * 考勤模块接口鉴权，与系分 §2.4.1 对齐：
 * <ul>
 *   <li>考勤组管理、月汇总：HR_STAFF 或 SYS_ADMIN</li>
 *   <li>打卡记录、统计查询：HR_STAFF、DEPT_MANAGER 或 SYS_ADMIN</li>
 *   <li>打卡/补卡：EMPLOYEE（门户自服务）</li>
 * </ul>
 */
public final class AttendanceAccessGuard {

    private AttendanceAccessGuard() {}

    /**
     * 要求考勤管理权限（写操作/管理端查看）
     * HR_STAFF 或 SYS_ADMIN
     */
    public static void requireHrStaff() {
        LoginUser user = SecurityUtils.requireLoginUser();
        if (user.hasRole(RoleCode.HR_STAFF.name()) || user.hasRole(RoleCode.SYS_ADMIN.name())) {
            return;
        }
        throw new ForbiddenException();
    }

    /**
     * 要求考勤统计查看权限
     * HR_STAFF、DEPT_MANAGER 或 SYS_ADMIN
     */
    public static void requireHrOrDeptMgr() {
        LoginUser user = SecurityUtils.requireLoginUser();
        if (user.hasRole(RoleCode.HR_STAFF.name())
                || user.hasRole(RoleCode.DEPT_MANAGER.name())
                || user.hasRole(RoleCode.SYS_ADMIN.name())) {
            return;
        }
        throw new ForbiddenException();
    }

    /**
     * 要求员工身份（门户自服务操作）
     * EMPLOYEE 或更高权限
     */
    public static void requireEmployee() {
        LoginUser user = SecurityUtils.requireLoginUser();
        if (user.hasRole(RoleCode.EMPLOYEE.name())
                || user.hasRole(RoleCode.HR_STAFF.name())
                || user.hasRole(RoleCode.SYS_ADMIN.name())) {
            return;
        }
        throw new ForbiddenException();
    }
}
