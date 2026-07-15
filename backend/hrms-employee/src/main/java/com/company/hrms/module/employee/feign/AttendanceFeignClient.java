package com.company.hrms.module.employee.feign;

import com.company.hrms.common.dto.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * hrms-attendance Feign 接口
 * 门户代理打卡、请假、加班请求
 */
@FeignClient(name = "hrms-attendance", path = "/api/v1")
public interface AttendanceFeignClient {

    // ===== 打卡 =====
    @PostMapping("/attendance/punch")
    Result<?> punch(@RequestBody Object dto);

    @PostMapping("/attendance/punch-fix")
    Result<?> applyPunchFix(@RequestBody Object dto);

    // ===== 请假 =====
    @GetMapping("/leaves/applications")
    Result<?> listLeaves(@RequestParam("employeeId") Long employeeId);

    @PostMapping("/leaves/applications")
    Result<?> applyLeave(@RequestBody Object dto);

    @GetMapping("/leaves/balances")
    Result<?> getLeaveBalances(@RequestParam("employeeId") Long employeeId);

    // ===== 加班 =====
    @GetMapping("/overtime/applications")
    Result<?> listOvertimes(@RequestParam("employeeId") Long employeeId);

    @PostMapping("/overtime/applications")
    Result<?> applyOvertime(@RequestBody Object dto);
}
