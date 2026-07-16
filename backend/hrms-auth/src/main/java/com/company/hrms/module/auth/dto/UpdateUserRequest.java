package com.company.hrms.module.auth.dto;

import java.util.List;

public class UpdateUserRequest {

    private Integer status;
    private List<Long> roleIds;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public List<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<Long> roleIds) {
        this.roleIds = roleIds;
    }
}
