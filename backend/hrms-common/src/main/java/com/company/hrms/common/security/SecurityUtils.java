package com.company.hrms.common.security;

import com.company.hrms.common.exception.UnauthorizedException;

/**
 * 当前登录用户工具：基于 ThreadLocal，由 JwtAuthFilter 在请求入口写入、结束时 clear。
 * <p>
 * 业务层通过 {@link #requireLoginUser()} / {@link #getUserId()} 取身份；
 * 切勿在异步线程中直接依赖本类（ThreadLocal 不跨线程）。
 */
public final class SecurityUtils {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private SecurityUtils() {
    }

    /** Filter 鉴权通过后写入；请求结束必须 {@link #clear()} */
    public static void setLoginUser(LoginUser user) {
        HOLDER.set(user);
    }

    /** 可能为 null（白名单接口未鉴权时） */
    public static LoginUser getLoginUser() {
        return HOLDER.get();
    }

    /** 系分命名别名：{@link #getLoginUser()} */
    public static LoginUser getCurrentUser() {
        return getLoginUser();
    }

    /** 未登录抛 401 */
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

    /** 防止 ThreadLocal 泄漏，Filter finally 中调用 */
    public static void clear() {
        HOLDER.remove();
    }
}
