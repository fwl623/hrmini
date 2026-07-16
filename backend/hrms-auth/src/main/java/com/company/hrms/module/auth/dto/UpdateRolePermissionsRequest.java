package com.company.hrms.module.auth.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public class UpdateRolePermissionsRequest {

    @NotNull
    private List<Long> permissionIds;

    public List<Long> getPermissionIds() {
        return permissionIds;
    }

    public void setPermissionIds(List<Long> permissionIds) {
        this.permissionIds = permissionIds;
    }
}
