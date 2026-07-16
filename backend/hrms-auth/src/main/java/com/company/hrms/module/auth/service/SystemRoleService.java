package com.company.hrms.module.auth.service;

import com.company.hrms.module.auth.dto.RoleVO;
import com.company.hrms.module.auth.entity.SysPermission;

import java.util.List;

public interface SystemRoleService {

    List<RoleVO> listRoles();

    void updateRolePermissions(Long roleId, List<Long> permissionIds);

    List<SysPermission> listPermissions();
}
