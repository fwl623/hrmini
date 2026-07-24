package com.company.hrms.module.auth.service;

import com.company.hrms.module.auth.dto.ChangePasswordRequest;
import com.company.hrms.module.auth.dto.LoginRequest;
import com.company.hrms.module.auth.dto.LoginResponse;
import com.company.hrms.module.auth.dto.PayslipVerifyRequest;
import com.company.hrms.module.auth.dto.ProfileResponse;

/**
 * 认证领域服务：登录态签发/失效、资料、改密、工资条二次验证。
 */
public interface AuthService {

    /** 登录；连续失败达上限锁定账号一段时间 */
    LoginResponse login(LoginRequest request, String clientIp, String userAgent);

    /** 登出；accessToken 可为空（尽力拉黑） */
    void logout(String accessToken);

    /** Refresh 轮换；校验 Redis 白名单中的 refreshToken */
    LoginResponse refresh(String refreshToken, String oldAccessToken);

    ProfileResponse profile();

    /** 校验旧密与强度后更新；作废当前 access/refresh */
    void changePassword(ChangePasswordRequest request, String accessToken);

    /** 禁用账号等场景：清 refresh / last-active / 权限缓存 */
    void invalidateUserSessions(Long userId);

    /** 首次绑定手机为登录名；已是手机号则须走 MOBILE_CHANGE */
    void bindMobile(String mobile, String smsCode);

    /** 业务上禁止解绑，引导走变更审批 */
    void unbindMobile(String smsCode);

    /** 工资条二次验证（密码或开发短信码） */
    void verifyPayslip(PayslipVerifyRequest request);
}
