package com.company.hrms.common.security;

import com.company.hrms.common.enums.DataScopeType;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 当前登录用户上下文（JWT 解析后放入 ThreadLocal）。
 */
public class LoginUser implements Serializable {

    private Long userId;
    private Long employeeId;
    private Long deptId;
    private String username;
    private DataScopeType dataScope = DataScopeType.SELF;
    private List<String> roles = Collections.emptyList();
    private Set<String> permissions = Collections.emptySet();

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

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public DataScopeType getDataScope() {
        return dataScope;
    }

    public void setDataScope(DataScopeType dataScope) {
        this.dataScope = dataScope == null || dataScope == DataScopeType.AUTO
                ? DataScopeType.SELF
                : dataScope;
    }

    public void setDataScope(String dataScope) {
        setDataScope(DataScopeType.from(dataScope));
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles == null ? Collections.emptyList() : roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions == null ? Collections.emptySet() : permissions;
    }

    public boolean hasRole(String roleCode) {
        return roles != null && roles.contains(roleCode);
    }

    public boolean hasPermission(String permissionCode) {
        return permissions != null && permissions.contains(permissionCode);
    }
}
