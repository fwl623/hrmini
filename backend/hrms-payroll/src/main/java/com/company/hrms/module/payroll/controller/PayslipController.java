package com.company.hrms.module.payroll.controller;

import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.module.payroll.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
public class PayslipController {

    @GetMapping("/payroll/payslips")
    public Result<Object> list(PageParam pageParam) {
        return Result.success(Map.of("list", Collections.emptyList(), "total", 0));
    }

    @GetMapping("/payroll/payslips/{month}")
    public Result<PayslipDetailVO> detail(@PathVariable String month) {
        return Result.success(new PayslipDetailVO());
    }

    @GetMapping("/profile/payslips")
    public Result<Object> portalList(PageParam pageParam) {
        Long empId = SecurityUtils.getCurrentUser().getEmployeeId();
        return Result.success(Map.of("list", Collections.emptyList(), "total", 0));
    }

    @GetMapping("/profile/payslips/trend")
    public Result<List<PayrollTrendVO>> trend() {
        return Result.success(Collections.emptyList());
    }

    @GetMapping("/profile/payslips/{period}")
    public Result<PayslipDetailVO> portalDetail(@PathVariable String period) {
        return Result.success(new PayslipDetailVO());
    }

    @PostMapping("/profile/payslips/verify")
    public Result<VerifyVO> verify(@RequestBody VerifyDTO dto) {
        return Result.success(new VerifyVO());
    }
}
