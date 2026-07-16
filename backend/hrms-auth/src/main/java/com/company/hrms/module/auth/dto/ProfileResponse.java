package com.company.hrms.module.auth.dto;

import java.util.List;

public class ProfileResponse {

    private Long userId;
    private Long employeeId;
    private String username;
    private List<String> roles;
    private List<String> permissions;
    private String dataScope;
    private boolean mustChangePassword;
    private String passwordExpiredAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public String getDataScope() {
        return dataScope;
    }

    public void setDataScope(String dataScope) {
        this.dataScope = dataScope;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public String getPasswordExpiredAt() {
        return passwordExpiredAt;
    }

    public void setPasswordExpiredAt(String passwordExpiredAt) {
        this.passwordExpiredAt = passwordExpiredAt;
    }
}
