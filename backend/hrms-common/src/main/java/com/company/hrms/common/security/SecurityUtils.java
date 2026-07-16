package com.company.hrms.common.security;

import com.company.hrms.common.exception.UnauthorizedException;

/**
 * 当前登录用户工具（ThreadLocal，由 JwtAuthFilter 写入）。
 */
public final class SecurityUtils {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private SecurityUtils() {
    }

    public static void setLoginUser(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser getLoginUser() {
        return HOLDER.get();
    }

    /** 系分命名别名：{@link #getLoginUser()} */
    public static LoginUser getCurrentUser() {
        return getLoginUser();
    }

    public static LoginUser requireLoginUser() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new UnauthorizedException();
        }
        return user;
    }

    public static Long getUserId() {
        return requireLoginUser().getUserId();
    }

    public static Long getEmployeeId() {
        return requireLoginUser().getEmployeeId();
    }

    /** 系分命名别名：{@link #getEmployeeId()} */
    public static Long getCurrentEmployeeId() {
        return getEmployeeId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
