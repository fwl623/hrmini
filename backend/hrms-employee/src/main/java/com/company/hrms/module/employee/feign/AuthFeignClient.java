package com.company.hrms.module.employee.feign;

import com.company.hrms.common.dto.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * hrms-auth Feign 接口
 * 创建员工账号、查询登录日志
 */
@FeignClient(name = "hrms-auth", path = "/api/v1")
public interface AuthFeignClient {

    /**
     * 创建系统账号（入职审批通过后调用）
     */
    @PostMapping("/system/users")
    Result<?> createAccount(@RequestBody Object dto);

    /**
     * 查询本人登录日志
     */
    @GetMapping("/profile/security/login-logs")
    Result<?> getLoginLogs(@RequestParam("userId") Long userId);

    /**
     * 修改密码
     */
    @PutMapping("/auth/password")
    Result<?> changePassword(@RequestBody Object dto);

    /**
     * 绑定手机
     */
    @PostMapping("/auth/mobile/bind")
    Result<?> bindMobile(@RequestBody Object dto);

    /**
     * 解绑手机
     */
    @DeleteMapping("/auth/mobile")
    Result<?> unbindMobile(@RequestBody Object dto);
}
