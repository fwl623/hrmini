package com.company.hrms.common.security;

import com.company.hrms.common.enums.RoleCode;
import com.company.hrms.common.exception.ForbiddenException;

/**
 * 接口级权限码校验（与菜单/角色管理里分配的 {@code permission.code} 对齐）。
 * <p>
 * {@link #requireAny}：当前用户持有任一码即通过；
 * {@code SYS_ADMIN} 兜底放行，避免种子权限遗漏导致管理员锁死。
 * 行级数据范围仍由 DataScope 处理，本类只管「能不能进这个 API」。
 */
public final class PermissionGuard {

    private PermissionGuard() {
    }

    /**
     * 要求持有 {@code permissionCodes} 中至少一个；否则 403。
     */
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

    /** 要求持有指定权限码 */
    public static void require(String permissionCode) {
        requireAny(permissionCode);
    }
}
