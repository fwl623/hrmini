package com.company.hrms.employee.service.impl;

import com.company.hrms.common.web.Result;
import com.company.hrms.employee.feign.AuthInternalFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Auth 内部 Feign 默认实现（联调占位）
 * <p>
 * 当 A 组 auth 模块未提供真实实现时，使用本默认实现记录日志。
 * 联调通过后，由 A 组提供真正的 FeignClient 或本地实现替换。
 * </p>
 */
@Slf4j
@Component
public class DefaultAuthInternalFeignClient implements AuthInternalFeignClient {

    @Override
    public Result<CreateUserResponse> createUser(CreateUserRequest request) {
        log.warn("[MOCK] AuthInternalFeignClient.createUser 被调用（联调占位）: username={}", request.getUsername());
        // 模拟返回 userId 以便流程继续
        CreateUserResponse resp = new CreateUserResponse();
        resp.setUserId(System.currentTimeMillis() % 10000);
        return Result.success(resp);
    }

    @Override
    public Result<Void> updateUsername(UpdateUsernameRequest request) {
        log.warn("[MOCK] AuthInternalFeignClient.updateUsername 被调用（联调占位）: userId={}, newUsername={}",
                request.getUserId(), request.getNewUsername());
        return Result.success();
    }
}
