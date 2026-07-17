package com.company.hrms.module.auth.controller;

import com.company.hrms.common.web.Result;
import com.company.hrms.module.auth.dto.BindMobileRequest;
import com.company.hrms.module.auth.dto.ChangePasswordRequest;
import com.company.hrms.module.auth.dto.LoginRequest;
import com.company.hrms.module.auth.dto.LoginResponse;
import com.company.hrms.module.auth.dto.PayslipVerifyRequest;
import com.company.hrms.module.auth.dto.ProfileResponse;
import com.company.hrms.module.auth.dto.RefreshTokenRequest;
import com.company.hrms.module.auth.dto.UnbindMobileRequest;
import com.company.hrms.module.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return Result.success(authService.login(request, clientIp(httpRequest), httpRequest.getHeader("User-Agent")));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        authService.logout(extractBearer(request));
        return Result.success();
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                         HttpServletRequest httpRequest) {
        return Result.success(authService.refresh(request.getRefreshToken(), extractBearer(httpRequest)));
    }

    @GetMapping("/profile")
    public Result<ProfileResponse> profile() {
        return Result.success(authService.profile());
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                       HttpServletRequest httpRequest) {
        authService.changePassword(request, extractBearer(httpRequest));
        return Result.success();
    }

    @PutMapping("/mobile")
    public Result<Void> bindMobile(@Valid @RequestBody BindMobileRequest request) {
        authService.bindMobile(request.getMobile(), request.getSmsCode());
        return Result.success();
    }

    @DeleteMapping("/mobile")
    public Result<Void> unbindMobile(@Valid @RequestBody UnbindMobileRequest request) {
        authService.unbindMobile(request.getSmsCode());
        return Result.success();
    }

    @PostMapping("/verify")
    public Result<Void> verify(@Valid @RequestBody PayslipVerifyRequest request) {
        authService.verifyPayslip(request);
        return Result.success();
    }

    private static String extractBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
