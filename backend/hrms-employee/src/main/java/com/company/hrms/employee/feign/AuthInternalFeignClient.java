package com.company.hrms.employee.feign;

import com.company.hrms.common.web.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * hrms-auth 内部接口定义（非 Feign 运行时，仅联调约定）
 *
 * ⚠️ 依赖 A 组 (李俊毅) 的 hrms-auth 模块提供真实实现
 * 联调前需与 A 组约定请求/响应字段
 *
 * 使用方式：联调时由 hrms-auth 提供 @RestController 实现本接口，
 *          或由 hrms-app 统一注册 FeignClient
 *
 * 职责：
 * 1. 入职建档：创建系统账号（sys_user）
 * 2. 手机号变更审批通过后：同步新手机号到 sys_user.username
 */
public interface AuthInternalFeignClient {

    /**
     * 创建员工系统账号
     * POST /api/v1/internal/users
     *
     * ⚠️ 入职建档联调 — 待 A 组提供接口后验证
     */
    @PostMapping("/api/v1/internal/users")
    Result<CreateUserResponse> createUser(@RequestBody CreateUserRequest request);

    /**
     * 更新登录名（手机号变更审批通过后同步）
     * PUT /api/v1/internal/users/username
     *
     * ⚠️ 手机号变更联调 — 待 A 组确认接口
     */
    @PutMapping("/api/v1/internal/users/username")
    Result<Void> updateUsername(@RequestBody UpdateUsernameRequest request);

    // ===== DTO =====

    class CreateUserRequest {
        private String username;
        private Long employeeId;
        private java.util.List<String> roleCodes;
        private String password;
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
        public java.util.List<String> getRoleCodes() { return roleCodes; }
        public void setRoleCodes(java.util.List<String> roleCodes) { this.roleCodes = roleCodes; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    class CreateUserResponse {
        private Long userId;
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
    }

    class UpdateUsernameRequest {
        private Long userId;
        private String newUsername;
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getNewUsername() { return newUsername; }
        public void setNewUsername(String newUsername) { this.newUsername = newUsername; }
    }
}
