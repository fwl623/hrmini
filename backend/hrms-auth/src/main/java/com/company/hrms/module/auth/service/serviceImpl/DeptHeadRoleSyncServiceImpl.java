package com.company.hrms.module.auth.service.serviceImpl;

import com.company.hrms.common.enums.RoleCode;
import com.company.hrms.module.auth.config.PermissionCacheManager;
import com.company.hrms.module.auth.mapper.DeptHeadAutoRoleMapper;
import com.company.hrms.module.auth.mapper.SysRoleMapper;
import com.company.hrms.module.auth.mapper.SysUserRoleMapper;
import com.company.hrms.module.auth.service.DeptHeadRoleSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 指定部门负责人 ↔ 自动授予/回收 DEPT_MANAGER。
 * <p>
 * 规则：新负责人无该角色则自动授予并写入来源标记表；
 * 卸任时仅当「不再担任任何部门负责人」且角色来源为自动授予才回收；
 * 手工在用户管理里勾选的 DEPT_MANAGER 不会被误删。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeptHeadRoleSyncServiceImpl implements DeptHeadRoleSyncService {

    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final DeptHeadAutoRoleMapper deptHeadAutoRoleMapper;
    private final PermissionCacheManager permissionCacheManager;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantAutoDeptManager(Long employeeId, Long userId) {
        if (employeeId == null) {
            return;
        }
        if (userId == null) {
            log.warn("部门负责人未绑定登录账号，跳过授予 DEPT_MANAGER employeeId={}", employeeId);
            return;
        }
        Long roleId = requireDeptManagerRoleId();
        int hasRole = sysUserRoleMapper.countByUserIdAndRoleId(userId, roleId);
        if (hasRole <= 0) {
            sysUserRoleMapper.insert(userId, roleId);
            deptHeadAutoRoleMapper.upsert(userId, employeeId);
            permissionCacheManager.evict(userId);
            log.info("因担任部门负责人自动授予 DEPT_MANAGER userId={} employeeId={}", userId, employeeId);
            return;
        }
        // 已有主管角色：仅当本就是自动授予时刷新标记；手工授予的不打标，避免日后误回收
        if (deptHeadAutoRoleMapper.countByUserId(userId) > 0) {
            deptHeadAutoRoleMapper.upsert(userId, employeeId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeAutoDeptManager(Long employeeId, Long userId, int remainingHeadDepts) {
        if (employeeId == null || remainingHeadDepts > 0) {
            return;
        }
        if (userId == null) {
            return;
        }
        if (deptHeadAutoRoleMapper.countByUserId(userId) <= 0) {
            log.debug("原负责人仍保留手工 DEPT_MANAGER，不回收 userId={} employeeId={}", userId, employeeId);
            return;
        }
        Long roleId = requireDeptManagerRoleId();
        sysUserRoleMapper.deleteByUserIdAndRoleId(userId, roleId);
        deptHeadAutoRoleMapper.deleteByUserId(userId);
        permissionCacheManager.evict(userId);
        log.info("卸任全部部门负责人，回收自动授予的 DEPT_MANAGER userId={} employeeId={}", userId, employeeId);
    }

    private Long requireDeptManagerRoleId() {
        Long roleId = sysRoleMapper.selectIdByCode(RoleCode.DEPT_MANAGER.name());
        if (roleId == null) {
            throw new IllegalStateException("系统未配置 DEPT_MANAGER 角色");
        }
        return roleId;
    }
}
