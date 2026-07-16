package com.company.hrms.common.util;

/**
 * 安全上下文工具类
 *
 * 从当前请求的 SecurityContext / Token 中获取当前用户信息。
 * 实际实现依赖 Spring Security / JWT 过滤器。
 */
public class SecurityUtils {

    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    public static void setCurrentUser(LoginUser user) {
        CONTEXT.set(user);
    }

    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 获取当前用户ID
     */
    public static Long getCurrentUserId() {
        LoginUser user = CONTEXT.get();
        return user != null ? user.getUserId() : null;
    }

    /**
     * 获取当前员工ID
     */
    public static Long getCurrentEmployeeId() {
        LoginUser user = CONTEXT.get();
        return user != null ? user.getEmployeeId() : null;
    }

    /**
     * 获取当前用户角色列表
     */
    public static java.util.List<String> getCurrentRoles() {
        LoginUser user = CONTEXT.get();
        return user != null ? user.getRoles() : java.util.Collections.emptyList();
    }

    /**
     * 获取当前用户数据范围
     */
    public static String getCurrentDataScope() {
        LoginUser user = CONTEXT.get();
        return user != null ? user.getDataScope() : "SELF";
    }

    /**
     * 当前登录用户信息
     */
    public static class LoginUser {
        private Long userId;
        private Long employeeId;
        private java.util.List<String> roles;
        private String dataScope;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

        public java.util.List<String> getRoles() { return roles; }
        public void setRoles(java.util.List<String> roles) { this.roles = roles; }

        public String getDataScope() { return dataScope; }
        public void setDataScope(String dataScope) { this.dataScope = dataScope; }
    }
}
