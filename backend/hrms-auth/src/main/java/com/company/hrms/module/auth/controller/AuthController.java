package com.company.hrms.module.auth.controller;

import com.company.hrms.common.web.Result;
import com.company.hrms.module.auth.crypto.LoginRsaCryptoService;
import com.company.hrms.module.auth.dto.BindMobileRequest;
import com.company.hrms.module.auth.dto.ChangePasswordRequest;
import com.company.hrms.module.auth.dto.LoginPublicKeyVO;
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

/**
 * 认证入口（登录 / 登出 / 刷新 / 资料 / 改密 / 工资条二次验证）。
 * <p>
 * 路径前缀 {@code /api/v1/auth}；登录与刷新在 JWT 白名单内，其余需 Bearer Token。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginRsaCryptoService loginRsaCryptoService;

    public AuthController(AuthService authService, LoginRsaCryptoService loginRsaCryptoService) {
        this.authService = authService;
        this.loginRsaCryptoService = loginRsaCryptoService;
    }

    /** 登录前拉取 RSA 公钥，前端加密 password 后再调用 /login */
    @GetMapping("/crypto/public-key")
    public Result<LoginPublicKeyVO> loginPublicKey() {
        return Result.success(new LoginPublicKeyVO(
                loginRsaCryptoService.getKeyId(),
                loginRsaCryptoService.getAlgorithm(),
                loginRsaCryptoService.getHash(),
                loginRsaCryptoService.getPublicKeySpkiBase64()));
    }

    /** 账号密码登录：失败计数、签发 Access/Refresh、写登录日志 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return Result.success(authService.login(request, clientIp(httpRequest), httpRequest.getHeader("User-Agent")));
    }

    /** 登出：Access Token 入黑名单并清 Refresh / last-active */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        authService.logout(extractBearer(request));
        return Result.success();
    }

    /** 用 Refresh Token 轮换一对新 Token（旧 Access 拉黑） */
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                         HttpServletRequest httpRequest) {
        return Result.success(authService.refresh(request.getRefreshToken(), extractBearer(httpRequest)));
    }

    /** 当前用户资料：角色、权限码、数据范围、是否须改密 */
    @GetMapping("/profile")
    public Result<ProfileResponse> profile() {
        return Result.success(authService.profile());
    }

    /** 修改密码成功后会使当前会话失效，需重新登录 */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                       HttpServletRequest httpRequest) {
        authService.changePassword(request, extractBearer(httpRequest));
        return Result.success();
    }

    /** 首次绑定手机号为登录名；已是手机号则须走 MOBILE_CHANGE 审批 */
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

    /** 工资条二次验证通过后，Redis 记 30 分钟免验证态 */
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
