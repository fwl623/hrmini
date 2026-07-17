package com.company.hrms.module.auth.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.common.config.HrmsSecurityProperties;
import com.company.hrms.common.enums.DataScopeType;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.exception.UnauthorizedException;
import com.company.hrms.common.security.JwtTokenProvider;
import com.company.hrms.common.security.LoginUser;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.module.auth.config.PermissionCacheManager;
import com.company.hrms.module.auth.constant.AuthRedisKeys;
import com.company.hrms.module.auth.dto.ChangePasswordRequest;
import com.company.hrms.module.auth.dto.LoginRequest;
import com.company.hrms.module.auth.dto.LoginResponse;
import com.company.hrms.module.auth.dto.PayslipVerifyRequest;
import com.company.hrms.module.auth.dto.ProfileResponse;
import com.company.hrms.module.auth.entity.LoginLog;
import com.company.hrms.module.auth.entity.SysUser;
import com.company.hrms.module.auth.mapper.LoginLogMapper;
import com.company.hrms.module.auth.mapper.SysUserMapper;
import com.company.hrms.module.auth.service.AuthService;
import io.jsonwebtoken.Claims;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class AuthServiceImpl implements AuthService {

    private static final int MAX_FAIL = 5;
    private static final long LOCK_SECONDS = 900;
    private static final long REFRESH_DAYS = 7;
    private static final long IDLE_MINUTES = 30;
    private static final int PASSWORD_EXPIRE_DAYS = 90;
    public static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");

    private final SysUserMapper sysUserMapper;
    private final LoginLogMapper loginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final HrmsSecurityProperties securityProperties;
    private final PermissionCacheManager permissionCacheManager;

    public AuthServiceImpl(SysUserMapper sysUserMapper,
                           LoginLogMapper loginLogMapper,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider jwtTokenProvider,
                           StringRedisTemplate redisTemplate,
                           HrmsSecurityProperties securityProperties,
                           PermissionCacheManager permissionCacheManager) {
        this.sysUserMapper = sysUserMapper;
        this.loginLogMapper = loginLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.redisTemplate = redisTemplate;
        this.securityProperties = securityProperties;
        this.permissionCacheManager = permissionCacheManager;
    }

    @Override
    public LoginResponse login(LoginRequest request, String clientIp, String userAgent) {
        String username = request.getUsername().trim();
        String failKey = AuthRedisKeys.loginFail(username);
        String failCountStr = redisTemplate.opsForValue().get(failKey);
        if (failCountStr != null && Long.parseLong(failCountStr) >= MAX_FAIL) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            incrLoginFail(failKey);
            saveLoginLog(user != null ? user.getId() : 0L, clientIp, userAgent, false, "账号不存在或已禁用");
            throw new UnauthorizedException("账号不存在或已禁用");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            long fails = incrLoginFail(failKey);
            saveLoginLog(user.getId(), clientIp, userAgent, false, "密码错误");
            if (fails >= MAX_FAIL) {
                throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
            }
            throw new UnauthorizedException("用户名或密码错误");
        }

        redisTemplate.delete(failKey);
        boolean mustChange = mustChangePassword(user);
        LoginUser loginUser = buildLoginUser(user);
        String accessToken = jwtTokenProvider.createAccessToken(loginUser);
        String refreshToken = UUID.randomUUID().toString().replace("-", "");
        storeRefreshToken(user.getId(), refreshToken);
        touchLastActive(user.getId());
        cachePermissions(user.getId(), loginUser.getPermissions());
        saveLoginLog(user.getId(), clientIp, userAgent, true, null);

        return new LoginResponse(
                accessToken,
                refreshToken,
                jwtTokenProvider.getAccessExpireSeconds(),
                mustChange
        );
    }

    @Override
    public void logout(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return;
        }
        try {
            Claims claims = jwtTokenProvider.parseClaimsAllowExpired(accessToken);
            blacklistAccessToken(claims.getId());
            Long userId = Long.valueOf(claims.getSubject());
            clearRefreshToken(userId);
            redisTemplate.delete(AuthRedisKeys.lastActive(userId));
        } catch (UnauthorizedException ignored) {
            // Token 已失效也视为登出成功
        } finally {
            SecurityUtils.clear();
        }
    }

    @Override
    public LoginResponse refresh(String refreshToken, String oldAccessToken) {
        String userIdStr = redisTemplate.opsForValue().get(AuthRedisKeys.refreshByToken(refreshToken));
        if (!StringUtils.hasText(userIdStr)) {
            throw new UnauthorizedException("Refresh Token 无效或已过期");
        }
        Long userId = Long.valueOf(userIdStr);
        String stored = redisTemplate.opsForValue().get(AuthRedisKeys.refreshByUser(userId));
        if (!refreshToken.equals(stored)) {
            throw new UnauthorizedException("Refresh Token 无效或已过期");
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            clearRefreshToken(userId);
            throw new UnauthorizedException("账号不存在或已禁用");
        }

        if (StringUtils.hasText(oldAccessToken)) {
            try {
                Claims oldClaims = jwtTokenProvider.parseClaimsAllowExpired(oldAccessToken);
                if (userId.equals(Long.valueOf(oldClaims.getSubject()))) {
                    blacklistAccessToken(oldClaims.getId());
                }
            } catch (UnauthorizedException ignored) {
                // 旧 access 无效则跳过拉黑
            }
        }

        clearRefreshToken(userId);
        LoginUser loginUser = buildLoginUser(user);
        String accessToken = jwtTokenProvider.createAccessToken(loginUser);
        String newRefresh = UUID.randomUUID().toString().replace("-", "");
        storeRefreshToken(userId, newRefresh);
        touchLastActive(userId);
        cachePermissions(userId, loginUser.getPermissions());

        return new LoginResponse(
                accessToken,
                newRefresh,
                jwtTokenProvider.getAccessExpireSeconds(),
                mustChangePassword(user)
        );
    }

    @Override
    public ProfileResponse profile() {
        LoginUser current = SecurityUtils.requireLoginUser();
        SysUser user = sysUserMapper.selectById(current.getUserId());
        if (user == null) {
            throw new UnauthorizedException();
        }
        List<String> roles = sysUserMapper.selectRoleCodesByUserId(user.getId());
        List<String> perms = sysUserMapper.selectPermissionCodesByUserId(user.getId());
        String dataScope = sysUserMapper.selectPrimaryDataScope(user.getId());
        if (dataScope == null) {
            dataScope = DataScopeType.SELF.name();
        }

        LocalDateTime changedAt = user.getPasswordChangedAt() != null
                ? user.getPasswordChangedAt() : user.getCreatedAt();
        LocalDate expiredAt = changedAt.toLocalDate().plusDays(PASSWORD_EXPIRE_DAYS);

        ProfileResponse resp = new ProfileResponse();
        resp.setUserId(user.getId());
        resp.setEmployeeId(user.getEmployeeId());
        resp.setUsername(user.getUsername());
        resp.setRoles(roles);
        resp.setPermissions(perms);
        resp.setDataScope(dataScope);
        resp.setMustChangePassword(mustChangePassword(user));
        resp.setPasswordExpiredAt(expiredAt.toString());
        return resp;
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request, String accessToken) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "两次输入的新密码不一致");
        }
        if (!PASSWORD_PATTERN.matcher(request.getNewPassword()).matches()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(),
                    "新密码须至少 8 位，且包含大小写字母和数字");
        }
        LoginUser current = SecurityUtils.requireLoginUser();
        SysUser user = sysUserMapper.selectById(current.getUserId());
        if (user == null) {
            throw new UnauthorizedException();
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("旧密码不正确");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "新密码不能与旧密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        clearRefreshToken(user.getId());
        if (StringUtils.hasText(accessToken)) {
            try {
                Claims claims = jwtTokenProvider.parseClaims(accessToken);
                blacklistAccessToken(claims.getId());
            } catch (UnauthorizedException ignored) {
                // 当前 Token 已失效则忽略
            }
        }
    }

    @Override
    @Transactional
    public void bindMobile(String mobile, String smsCode) {
        assertDevSmsCode(smsCode);
        LoginUser current = SecurityUtils.requireLoginUser();
        SysUser user = sysUserMapper.selectById(current.getUserId());
        if (user == null) {
            throw new UnauthorizedException();
        }
        // 仅允许首次绑定（当前登录名非 11 位手机号时）；变更须走 MOBILE_CHANGE 审批
        if (isMobileUsername(user.getUsername())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(),
                    "手机号变更须走审批流程，不可直接修改");
        }
        Long exists = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, mobile)
                .ne(SysUser::getId, current.getUserId()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT.getCode(), "手机号已被占用");
        }
        user.setUsername(mobile);
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
    }

    @Override
    public void unbindMobile(String smsCode) {
        assertDevSmsCode(smsCode);
        throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(),
                "解绑后将无法登录，请走手机号变更审批流程");
    }

    @Override
    public void verifyPayslip(PayslipVerifyRequest request) {
        LoginUser current = SecurityUtils.requireLoginUser();
        SysUser user = sysUserMapper.selectById(current.getUserId());
        if (user == null) {
            throw new UnauthorizedException();
        }
        boolean ok;
        if ("PASSWORD".equalsIgnoreCase(request.getVerifyType())) {
            ok = passwordEncoder.matches(request.getVerifyCode(), user.getPasswordHash());
        } else if ("SMS".equalsIgnoreCase(request.getVerifyType())) {
            ok = isDevSmsCode(request.getVerifyCode());
        } else {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "verifyType 须为 PASSWORD 或 SMS");
        }
        if (!ok) {
            throw new BusinessException(ErrorCode.PAYSLIP_VERIFY_FAILED);
        }
        redisTemplate.opsForValue().set(
                AuthRedisKeys.payslipVerified(current.getUserId()),
                "1",
                30,
                TimeUnit.MINUTES
        );
    }

    public boolean isTokenBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(AuthRedisKeys.tokenBlacklist(jti)));
    }

    public boolean isIdleTimeout(Long userId) {
        return !Boolean.TRUE.equals(redisTemplate.hasKey(AuthRedisKeys.lastActive(userId)));
    }

    public void touchLastActive(Long userId) {
        redisTemplate.opsForValue().set(
                AuthRedisKeys.lastActive(userId),
                String.valueOf(System.currentTimeMillis()),
                IDLE_MINUTES,
                TimeUnit.MINUTES
        );
    }

    public LoginUser buildLoginUser(SysUser user) {
        List<String> roles = sysUserMapper.selectRoleCodesByUserId(user.getId());
        java.util.Set<String> perms = loadPermissions(user.getId());
        String dataScope = sysUserMapper.selectPrimaryDataScope(user.getId());
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setEmployeeId(user.getEmployeeId());
        if (user.getEmployeeId() != null) {
            loginUser.setDeptId(sysUserMapper.selectDeptIdByEmployeeId(user.getEmployeeId()));
        }
        loginUser.setRoles(roles);
        loginUser.setPermissions(perms);
        loginUser.setDataScope(dataScope);
        return loginUser;
    }

    /** 供 Filter 判断强制改密 */
    public boolean requiresPasswordChange(SysUser user) {
        return mustChangePassword(user);
    }

    @Override
    public void invalidateUserSessions(Long userId) {
        if (userId == null) {
            return;
        }
        clearRefreshToken(userId);
        redisTemplate.delete(AuthRedisKeys.lastActive(userId));
        permissionCacheManager.evict(userId);
        redisTemplate.delete(AuthRedisKeys.payslipVerified(userId));
    }

    private java.util.Set<String> loadPermissions(Long userId) {
        String key = AuthRedisKeys.permissions(userId);
        java.util.Set<String> cached = redisTemplate.opsForSet().members(key);
        if (cached != null && !cached.isEmpty()) {
            return new HashSet<>(cached);
        }
        List<String> perms = sysUserMapper.selectPermissionCodesByUserId(userId);
        java.util.Set<String> set = new HashSet<>(perms);
        cachePermissions(userId, set);
        return set;
    }

    private void blacklistAccessToken(String jti) {
        if (!StringUtils.hasText(jti)) {
            return;
        }
        redisTemplate.opsForValue().set(
                AuthRedisKeys.tokenBlacklist(jti),
                "1",
                jwtTokenProvider.getAccessExpireSeconds(),
                TimeUnit.SECONDS
        );
    }

    private void assertDevSmsCode(String smsCode) {
        if (!isDevSmsCode(smsCode)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "短信验证码错误");
        }
    }

    private boolean isDevSmsCode(String smsCode) {
        HrmsSecurityProperties.Sms sms = securityProperties.getSms();
        return sms.isDevEnabled()
                && StringUtils.hasText(sms.getDevCode())
                && sms.getDevCode().equals(smsCode);
    }

    private static boolean isMobileUsername(String username) {
        return username != null && username.matches("^1\\d{10}$");
    }

    private long incrLoginFail(String failKey) {
        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(failKey, LOCK_SECONDS, TimeUnit.SECONDS);
        }
        if (count != null && count >= MAX_FAIL) {
            redisTemplate.expire(failKey, LOCK_SECONDS, TimeUnit.SECONDS);
        }
        return count == null ? 0 : count;
    }

    private void storeRefreshToken(Long userId, String refreshToken) {
        long ttlSeconds = TimeUnit.DAYS.toSeconds(REFRESH_DAYS);
        redisTemplate.opsForValue().set(AuthRedisKeys.refreshByUser(userId), refreshToken, ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(AuthRedisKeys.refreshByToken(refreshToken), String.valueOf(userId), ttlSeconds, TimeUnit.SECONDS);
    }

    private void clearRefreshToken(Long userId) {
        String old = redisTemplate.opsForValue().get(AuthRedisKeys.refreshByUser(userId));
        redisTemplate.delete(AuthRedisKeys.refreshByUser(userId));
        if (StringUtils.hasText(old)) {
            redisTemplate.delete(AuthRedisKeys.refreshByToken(old));
        }
    }

    private void cachePermissions(Long userId, java.util.Set<String> permissions) {
        String key = AuthRedisKeys.permissions(userId);
        permissionCacheManager.evict(userId);
        if (permissions != null && !permissions.isEmpty()) {
            redisTemplate.opsForSet().add(key, permissions.toArray(new String[0]));
            // 系分：权限缓存 TTL 10min，变更时主动 DEL
            redisTemplate.expire(key, 10, TimeUnit.MINUTES);
        }
    }

    private boolean mustChangePassword(SysUser user) {
        LocalDateTime changedAt = user.getPasswordChangedAt();
        if (changedAt == null) {
            return true;
        }
        if (user.getCreatedAt() != null
                && Math.abs(ChronoUnit.SECONDS.between(user.getCreatedAt(), changedAt)) <= 2) {
            return true;
        }
        return ChronoUnit.DAYS.between(changedAt.toLocalDate(), LocalDate.now()) >= PASSWORD_EXPIRE_DAYS;
    }

    private void saveLoginLog(Long userId, String ip, String ua, boolean success, String failReason) {
        LoginLog log = new LoginLog();
        log.setUserId(userId == null ? 0L : userId);
        log.setLoginTime(LocalDateTime.now());
        log.setLoginIp(ip == null ? "unknown" : ip);
        log.setUserAgent(ua);
        log.setDevice(parseDevice(ua));
        log.setSuccess(success ? 1 : 0);
        log.setFailReason(failReason);
        log.setCreatedAt(LocalDateTime.now());
        loginLogMapper.insert(log);
    }

    private static String parseDevice(String ua) {
        if (ua == null) {
            return "unknown";
        }
        String lower = ua.toLowerCase();
        if (lower.contains("mobile")) {
            return "mobile";
        }
        if (lower.contains("windows")) {
            return "windows";
        }
        if (lower.contains("mac")) {
            return "mac";
        }
        return "web";
    }
}
