package com.company.hrms.module.auth.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.security.PermissionGuard;
import com.company.hrms.module.auth.config.PermissionCacheManager;
import com.company.hrms.module.auth.dto.RoleVO;
import com.company.hrms.module.auth.entity.SysPermission;
import com.company.hrms.module.auth.entity.SysRole;
import com.company.hrms.module.auth.mapper.SysPermissionMapper;
import com.company.hrms.module.auth.mapper.SysRoleMapper;
import com.company.hrms.module.auth.mapper.SysRolePermissionMapper;
import com.company.hrms.module.auth.mapper.SysUserRoleMapper;
import com.company.hrms.module.auth.service.SystemRoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统角色管理：预置角色列表、改显示名、分配权限树。
 * <p>
 * 角色编码只读不可增删；改权限后对该角色下所有用户 evict 权限缓存。
 */
@Service
public class SystemRoleServiceImpl implements SystemRoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PermissionCacheManager permissionCacheManager;

    public SystemRoleServiceImpl(SysRoleMapper sysRoleMapper,
                                 SysPermissionMapper sysPermissionMapper,
                                 SysRolePermissionMapper sysRolePermissionMapper,
                                 SysUserRoleMapper sysUserRoleMapper,
                                 PermissionCacheManager permissionCacheManager) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.permissionCacheManager = permissionCacheManager;
    }

    @Override
    public List<RoleVO> listRoles() {
        requireRoleView();
        List<SysRole> roles = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId));
        return roles.stream().map(r -> {
            RoleVO vo = new RoleVO();
            vo.setId(r.getId());
            vo.setCode(r.getCode());
            vo.setName(r.getName());
            vo.setDataScope(r.getDataScope());
            vo.setPermissionIds(sysRoleMapper.selectPermissionIdsByRoleId(r.getId()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateRole(Long roleId, String name) {
        requireRoleEdit();
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "角色不存在");
        }
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "角色名称不能为空");
        }
        role.setName(name.trim());
        role.setUpdatedAt(LocalDateTime.now());
        sysRoleMapper.updateById(role);
    }

    @Override
    @Transactional
    public void updateRolePermissions(Long roleId, List<Long> permissionIds) {
        requireRoleEdit();
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "角色不存在");
        }
        sysRolePermissionMapper.deleteByRoleId(roleId);
        if (permissionIds != null) {
            for (Long pid : permissionIds) {
                sysRolePermissionMapper.insert(roleId, pid);
            }
        }
        permissionCacheManager.evictAll(sysUserRoleMapper.selectUserIdsByRoleId(roleId));
    }

    @Override
    public List<SysPermission> listPermissions() {
        requireRoleView();
        return sysPermissionMapper.selectList(new LambdaQueryWrapper<SysPermission>().orderByAsc(SysPermission::getId));
    }

    private void requireRoleView() {
        PermissionGuard.requireAny("system:role:view", "menu:system");
    }

    private void requireRoleEdit() {
        PermissionGuard.requireAny("system:role:edit", "menu:system");
    }
}
