package com.company.hrms.common.security;

import com.company.hrms.common.enums.DataScopeType;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 当前登录用户上下文（JWT 解析后由 {@link SecurityUtils} 放入 ThreadLocal）。
 * <p>
 * 字段用途：
 * <ul>
 *   <li>{@code userId} — sys_user 主键</li>
 *   <li>{@code employeeId} — 关联员工档案（可空，如纯管理员账号）</li>
 *   <li>{@code deptId} — 员工所属部门，供 DataScope DEPT_TREE 拼 SQL</li>
 *   <li>{@code roles} / {@code permissions} — RBAC；权限码可经角色管理动态分配</li>
 *   <li>{@code dataScope} — 行级数据范围（多角色取最宽，见 AuthServiceImpl）</li>
 * </ul>
 * 未使用 Lombok {@code @Data}：部分 setter 含空值兜底与重载，需手写保持约束可见。
 */
public class LoginUser implements Serializable {

    /** sys_user.id */
    private Long userId;
    /** 关联 employee.id，无档案时为 null */
    private Long employeeId;
    /** 员工当前部门，DEPT_TREE 数据范围用 */
    private Long deptId;
    private String username;
    /** 默认 SELF，避免未赋值时误放行 */
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

    /** null / AUTO 一律落到 SELF，防止空范围被当成「不过滤」 */
    public void setDataScope(DataScopeType dataScope) {
        this.dataScope = dataScope == null || dataScope == DataScopeType.AUTO
                ? DataScopeType.SELF
                : dataScope;
    }

    /** 兼容 DB / Profile 返回的字符串 data_scope */
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
