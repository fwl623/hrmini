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
import com.company.hrms.module.auth.service.InternalUserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class InternalUserServiceImpl implements InternalUserService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public InternalUserServiceImpl(SysUserMapper sysUserMapper,
                                   SysRoleMapper sysRoleMapper,
                                   SysUserRoleMapper sysUserRoleMapper,
                                   PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.passwordEncoder = passwordEncoder;
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
        user.setPasswordHash(passwordEncoder.encode(randomPassword()));
        user.setPasswordChangedAt(now);
        user.setEmployeeId(request.getEmployeeId());
        user.setStatus(1);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        sysUserMapper.insert(user);

        List<String> roleCodes = CollectionUtils.isEmpty(request.getRoleCodes())
                ? Collections.singletonList(RoleCode.EMPLOYEE.name())
                : request.getRoleCodes();
        boolean anyRole = false;
        for (String code : roleCodes) {
            SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, code));
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
