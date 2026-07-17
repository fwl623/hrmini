package com.company.hrms.module.auth.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.hrms.common.enums.RoleCode;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.exception.ForbiddenException;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.module.auth.config.PermissionCacheManager;
import com.company.hrms.module.auth.dto.CreateUserRequest;
import com.company.hrms.module.auth.dto.UpdateUserRequest;
import com.company.hrms.module.auth.dto.UserVO;
import com.company.hrms.module.auth.entity.LoginLog;
import com.company.hrms.module.auth.entity.OperationLog;
import com.company.hrms.module.auth.entity.SysUser;
import com.company.hrms.module.auth.mapper.LoginLogMapper;
import com.company.hrms.module.auth.mapper.OperationLogMapper;
import com.company.hrms.module.auth.mapper.SysUserMapper;
import com.company.hrms.module.auth.mapper.SysUserRoleMapper;
import com.company.hrms.module.auth.service.AuthService;
import com.company.hrms.module.auth.service.SystemUserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SystemUserServiceImpl implements SystemUserService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final LoginLogMapper loginLogMapper;
    private final OperationLogMapper operationLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final PermissionCacheManager permissionCacheManager;
    private final AuthService authService;

    public SystemUserServiceImpl(SysUserMapper sysUserMapper,
                                 SysUserRoleMapper sysUserRoleMapper,
                                 LoginLogMapper loginLogMapper,
                                 OperationLogMapper operationLogMapper,
                                 PasswordEncoder passwordEncoder,
                                 PermissionCacheManager permissionCacheManager,
                                 AuthService authService) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.loginLogMapper = loginLogMapper;
        this.operationLogMapper = operationLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.permissionCacheManager = permissionCacheManager;
        this.authService = authService;
    }

    @Override
    public PageResult<UserVO> pageUsers(String keyword, PageParam pageParam) {
        requireSysAdmin();
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.like(SysUser::getUsername, keyword.trim());
        }
        qw.orderByDesc(SysUser::getId);
        Page<SysUser> page = sysUserMapper.selectPage(
                new Page<>(pageParam.getPage(), pageParam.getPageSize()), qw);
        List<UserVO> list = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(list, page.getTotal(), pageParam);
    }

    @Override
    @Transactional
    public Long createUser(CreateUserRequest request) {
        requireSysAdmin();
        Long exists = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT.getCode(), "用户名已存在");
        }
        String rawPassword;
        if (StringUtils.hasText(request.getPassword())) {
            if (!AuthServiceImpl.PASSWORD_PATTERN.matcher(request.getPassword()).matches()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(),
                        "密码须至少 8 位，且包含大小写字母和数字");
            }
            rawPassword = request.getPassword();
        } else {
            rawPassword = randomPassword();
        }
        LocalDateTime now = LocalDateTime.now();
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setPasswordChangedAt(now);
        user.setEmployeeId(request.getEmployeeId());
        user.setStatus(1);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        sysUserMapper.insert(user);
        for (Long roleId : request.getRoleIds()) {
            sysUserRoleMapper.insert(user.getId(), roleId);
        }
        return user.getId();
    }

    @Override
    @Transactional
    public void updateUser(Long id, UpdateUserRequest request) {
        requireSysAdmin();
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "用户不存在");
        }
        if (request.getStatus() != null) {
            Integer oldStatus = user.getStatus();
            user.setStatus(request.getStatus());
            user.setUpdatedAt(LocalDateTime.now());
            sysUserMapper.updateById(user);
            if (request.getStatus() != 1 && (oldStatus == null || oldStatus == 1)) {
                authService.invalidateUserSessions(id);
            }
        }
        if (request.getRoleIds() != null) {
            sysUserRoleMapper.deleteByUserId(id);
            for (Long roleId : request.getRoleIds()) {
                sysUserRoleMapper.insert(id, roleId);
            }
            permissionCacheManager.evict(id);
        }
    }

    @Override
    public PageResult<LoginLog> pageLoginLogs(PageParam pageParam) {
        requireSysAdmin();
        Page<LoginLog> page = loginLogMapper.selectPage(
                new Page<>(pageParam.getPage(), pageParam.getPageSize()),
                new LambdaQueryWrapper<LoginLog>().orderByDesc(LoginLog::getLoginTime));
        return PageResult.of(page.getRecords(), page.getTotal(), pageParam);
    }

    @Override
    public PageResult<OperationLog> pageOperationLogs(PageParam pageParam) {
        requireSysAdmin();
        Page<OperationLog> page = operationLogMapper.selectPage(
                new Page<>(pageParam.getPage(), pageParam.getPageSize()),
                new LambdaQueryWrapper<OperationLog>().orderByDesc(OperationLog::getCreatedAt));
        return PageResult.of(page.getRecords(), page.getTotal(), pageParam);
    }

    private void requireSysAdmin() {
        if (!SecurityUtils.requireLoginUser().hasRole(RoleCode.SYS_ADMIN.name())) {
            throw new ForbiddenException();
        }
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmployeeId(user.getEmployeeId());
        vo.setStatus(user.getStatus());
        vo.setRoles(sysUserMapper.selectRoleCodesByUserId(user.getId()));
        return vo;
    }

    private static String randomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder("Aa1");
        for (int i = 0; i < 9; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
