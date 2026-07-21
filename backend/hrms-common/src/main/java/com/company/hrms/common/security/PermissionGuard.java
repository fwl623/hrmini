package com.company.hrms.common.security;

import com.company.hrms.common.enums.RoleCode;
import com.company.hrms.common.exception.ForbiddenException;

/**
 * 权限码校验：角色权限分配写入的 code 与菜单/API 对齐。
 * SYS_ADMIN 保留兜底（避免种子权限遗漏导致管理员被锁死）。
 */
public final class PermissionGuard {

    private PermissionGuard() {
    }

    public static void requireAny(String... permissionCodes) {
        LoginUser user = SecurityUtils.requireLoginUser();
        if (user.hasRole(RoleCode.SYS_ADMIN.name())) {
            return;
        }
        if (permissionCodes != null) {
            for (String code : permissionCodes) {
                if (code != null && user.hasPermission(code)) {
                    return;
                }
            }
        }
        throw new ForbiddenException();
    }

    public static void require(String permissionCode) {
        requireAny(permissionCode);
    }
}
