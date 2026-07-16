package com.company.hrms.module.auth.constant;

/**
 * Auth 模块 Redis Key 约定。
 */
public final class AuthRedisKeys {

    private AuthRedisKeys() {
    }

    /** 登录失败计数 login:fail:{username} */
    public static String loginFail(String username) {
        return "hrms:login:fail:" + username;
    }

    /** Access Token 黑名单 token:blacklist:{jti} */
    public static String tokenBlacklist(String jti) {
        return "hrms:token:blacklist:" + jti;
    }

    /** Refresh Token 白名单（按用户） refresh:{userId} → token */
    public static String refreshByUser(Long userId) {
        return "hrms:refresh:" + userId;
    }

    /** Refresh Token → userId，便于 refresh 接口反查 */
    public static String refreshByToken(String refreshToken) {
        return "hrms:refresh:token:" + refreshToken;
    }

    /** 无操作超时 user:last-active:{userId} */
    public static String lastActive(Long userId) {
        return "hrms:user:last-active:" + userId;
    }

    /** 权限码缓存 user:perms:{userId} */
    public static String permissions(Long userId) {
        return "hrms:user:perms:" + userId;
    }

    /** 工资条二次验证 */
    public static String payslipVerified(Long userId) {
        return "hrms:payslip:verified:" + userId;
    }
}
