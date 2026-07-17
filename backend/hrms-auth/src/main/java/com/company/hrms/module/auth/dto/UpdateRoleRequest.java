package com.company.hrms.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 角色编辑：仅允许改名称，编码只读 */
public class UpdateRoleRequest {

    @NotBlank
    @Size(max = 64)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
