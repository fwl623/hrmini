package com.company.hrms.module.auth.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.common.enums.RoleCode;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.module.auth.dto.InternalCreateUserRequest;
import com.company.hrms.module.auth.entity.SysRole;
import com.company.hrms.module.auth.entity.SysUser;
import com.company.hrms.module.auth.mapper.SysRoleMapper;
import com.company.hrms.module.auth.mapper.SysUserMapper;
import com.company.hrms.module.auth.mapper.SysUserRoleMapper;
import com.company.hrms.module.auth.service.AuthService;
import com.company.hrms.module.auth.service.InternalUserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class InternalUserServiceImpl implements InternalUserService {

    /** 入职/内部建号默认初密（联调与种子账号一致，便于登录） */
    private static final String DEFAULT_PASSWORD = "Admin@123";

    /** 内部建号允许的角色；禁止 SYS_ADMIN，防止持内部 Token 提权 */
    private static final Set<String> ALLOWED_ROLE_CODES = Set.of(
            RoleCode.EMPLOYEE.name(),
            RoleCode.DEPT_MANAGER.name(),
            RoleCode.HR_STAFF.name(),
            RoleCode.FINANCE.name()
    );

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public InternalUserServiceImpl(SysUserMapper sysUserMapper,
                                   SysRoleMapper sysRoleMapper,
                                   SysUserRoleMapper sysUserRoleMapper,
                                   PasswordEncoder passwordEncoder,
                                   AuthService authService) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    @Override
    @Transactional
    public Long createUser(InternalCreateUserRequest request) {
        Long exists = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT.getCode(), "用户名已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        // 早于 createdAt，避免 mustChangePassword 判定为「首次强制改密」
        user.setPasswordChangedAt(now.minusDays(1));
        user.setCreatedAt(now);
        user.setEmployeeId(request.getEmployeeId());
        user.setStatus(1);
        user.setUpdatedAt(now);
        sysUserMapper.insert(user);

        List<String> roleCodes = CollectionUtils.isEmpty(request.getRoleCodes())
                ? Collections.singletonList(RoleCode.EMPLOYEE.name())
                : request.getRoleCodes();
        boolean anyRole = false;
        for (String code : roleCodes) {
            if (code == null || !ALLOWED_ROLE_CODES.contains(code.trim().toUpperCase())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(),
                        "内部建号不允许分配该角色（禁止 SYS_ADMIN）");
            }
            String normalized = code.trim().toUpperCase();
            SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, normalized));
            if (role != null) {
                sysUserRoleMapper.insert(user.getId(), role.getId());
                anyRole = true;
            }
        }
        if (!anyRole) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "角色编码无效");
        }
        return user.getId();
    }

    @Override
    @Transactional
    public void updateStatus(Long userId, Integer status) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "用户不存在");
        }
        user.setStatus(status);
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        if (status != null && status != 1) {
            authService.invalidateUserSessions(userId);
        }
    }

    @Override
    @Transactional
    public void updateUsername(Long userId, String username) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "用户不存在");
        }
        Long exists = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .ne(SysUser::getId, userId));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT.getCode(), "用户名已存在");
        }
        user.setUsername(username);
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
    }

}
