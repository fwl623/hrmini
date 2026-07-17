package com.company.hrms.module.auth.controller;

import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.module.auth.dto.CreateUserRequest;
import com.company.hrms.module.auth.dto.RoleVO;
import com.company.hrms.module.auth.dto.UpdateRolePermissionsRequest;
import com.company.hrms.module.auth.dto.UpdateRoleRequest;
import com.company.hrms.module.auth.dto.UpdateUserRequest;
import com.company.hrms.module.auth.dto.UserVO;
import com.company.hrms.module.auth.entity.LoginLog;
import com.company.hrms.module.auth.entity.OperationLog;
import com.company.hrms.module.auth.entity.SysPermission;
import com.company.hrms.module.auth.service.SystemRoleService;
import com.company.hrms.module.auth.service.SystemUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/system")
public class SystemController {

    private final SystemUserService systemUserService;
    private final SystemRoleService systemRoleService;

    public SystemController(SystemUserService systemUserService, SystemRoleService systemRoleService) {
        this.systemUserService = systemUserService;
        this.systemRoleService = systemRoleService;
    }

    @GetMapping("/users")
    public Result<PageResult<UserVO>> pageUsers(
            @RequestParam(required = false) String keyword,
            @Valid PageParam pageParam) {
        return Result.success(systemUserService.pageUsers(keyword, pageParam));
    }

    @PostMapping("/users")
    public Result<Map<String, Long>> createUser(@Valid @RequestBody CreateUserRequest request) {
        Long id = systemUserService.createUser(request);
        return Result.success(Map.of("userId", id));
    }

    @PutMapping("/users/{id}")
    public Result<Void> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        systemUserService.updateUser(id, request);
        return Result.success();
    }

    @GetMapping("/roles")
    public Result<List<RoleVO>> listRoles() {
        return Result.success(systemRoleService.listRoles());
    }

    @PutMapping("/roles/{id}")
    public Result<Void> updateRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        systemRoleService.updateRole(id, request.getName());
        return Result.success();
    }

    @PutMapping("/roles/{id}/permissions")
    public Result<Void> updateRolePermissions(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRolePermissionsRequest request) {
        systemRoleService.updateRolePermissions(id, request.getPermissionIds());
        return Result.success();
    }

    @GetMapping("/permissions")
    public Result<List<SysPermission>> listPermissions() {
        return Result.success(systemRoleService.listPermissions());
    }

    @GetMapping("/login-logs")
    public Result<PageResult<LoginLog>> loginLogs(@Valid PageParam pageParam) {
        return Result.success(systemUserService.pageLoginLogs(pageParam));
    }

    @GetMapping("/operation-logs")
    public Result<PageResult<OperationLog>> operationLogs(@Valid PageParam pageParam) {
        return Result.success(systemUserService.pageOperationLogs(pageParam));
    }
}
