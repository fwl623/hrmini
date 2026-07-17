package com.company.hrms.module.auth.service;

import com.company.hrms.module.auth.dto.ChangePasswordRequest;
import com.company.hrms.module.auth.dto.LoginRequest;
import com.company.hrms.module.auth.dto.LoginResponse;
import com.company.hrms.module.auth.dto.PayslipVerifyRequest;
import com.company.hrms.module.auth.dto.ProfileResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request, String clientIp, String userAgent);

    void logout(String accessToken);

    LoginResponse refresh(String refreshToken, String oldAccessToken);

    ProfileResponse profile();

    void changePassword(ChangePasswordRequest request, String accessToken);

    /** 禁用账号等场景：清 refresh / last-active / 权限缓存 */
    void invalidateUserSessions(Long userId);

    void bindMobile(String mobile, String smsCode);

    void unbindMobile(String smsCode);

    void verifyPayslip(PayslipVerifyRequest request);
}
