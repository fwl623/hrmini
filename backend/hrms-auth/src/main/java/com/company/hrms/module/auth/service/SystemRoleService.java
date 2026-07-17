package com.company.hrms.module.auth.service;

import com.company.hrms.module.auth.dto.RoleVO;
import com.company.hrms.module.auth.entity.SysPermission;

import java.util.List;

public interface SystemRoleService {

    List<RoleVO> listRoles();

    /** 仅更新角色名称，编码不可改 */
    void updateRole(Long roleId, String name);

    void updateRolePermissions(Long roleId, List<Long> permissionIds);

    List<SysPermission> listPermissions();
}
