package com.company.hrms.module.auth.controller;

import com.company.hrms.common.web.Result;
import com.company.hrms.module.auth.dto.InternalCreateUserRequest;
import com.company.hrms.module.auth.service.InternalUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 内部接口：供入职/离职等模块调用。
 * 须在请求头携带 {@code X-Internal-Token}（配置项 {@code hrms.internal.token}）。
 */
@RestController
@RequestMapping("/internal")
public class InternalUserController {

    private final InternalUserService internalUserService;

    public InternalUserController(InternalUserService internalUserService) {
        this.internalUserService = internalUserService;
    }

    @PostMapping("/users")
    public Result<Map<String, Long>> createUser(@Valid @RequestBody InternalCreateUserRequest request) {
        Long id = internalUserService.createUser(request);
        return Result.success(Map.of("userId", id));
    }

    @PutMapping("/users/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        internalUserService.updateStatus(id, status);
        return Result.success();
    }

    @PutMapping("/users/{id}/username")
    public Result<Void> updateUsername(@PathVariable Long id, @RequestParam String username) {
        internalUserService.updateUsername(id, username);
        return Result.success();
    }
}
