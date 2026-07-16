package com.company.hrms.module.auth.service;

import com.company.hrms.module.auth.dto.ChangePasswordRequest;
import com.company.hrms.module.auth.dto.LoginRequest;
import com.company.hrms.module.auth.dto.LoginResponse;
import com.company.hrms.module.auth.dto.PayslipVerifyRequest;
import com.company.hrms.module.auth.dto.ProfileResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request, String clientIp, String userAgent);

    void logout(String accessToken);

    LoginResponse refresh(String refreshToken);

    ProfileResponse profile();

    void changePassword(ChangePasswordRequest request, String accessToken);

    void bindMobile(String mobile, String smsCode);

    void unbindMobile(String smsCode);

    void verifyPayslip(PayslipVerifyRequest request);
}
