package com.company.hrms.module.attendance.controller;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.Result;
import com.company.hrms.module.attendance.dto.MonthlySummaryLockDTO;
import com.company.hrms.module.attendance.dto.MonthlySummaryVO;
import com.company.hrms.module.attendance.auth.AttendanceAccessGuard;
import com.company.hrms.module.attendance.service.SummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 月考勤汇总 Controller
 */
@RestController
@RequestMapping("/attendance")
@RequiredArgsConstructor
public class MonthlySummaryController {

    private final SummaryService summaryService;

    /**
     * 月汇总查看
     * GET /api/v1/attendance/monthly-summary?period=2026-07&page=1&pageSize=20
     */
    @GetMapping("/monthly-summary")
    public Result<MonthlySummaryVO> get(PageParam pageParam,
                                        @RequestParam String period) {
        AttendanceAccessGuard.requireHrStaff();
        return Result.success(summaryService.getMonthlySummary(pageParam, period));
    }

    /**
     * 月汇总锁定/解锁
     * PUT /api/v1/attendance/monthly-summary
     */
    @PutMapping("/monthly-summary")
    public Result<Void> updateLock(@RequestBody MonthlySummaryLockDTO dto) {
        AttendanceAccessGuard.requireHrStaff();
        Long operatorId = SecurityUtils.getCurrentUser().getEmployeeId();
        if (operatorId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "当前账号未绑定员工");
        }
        summaryService.updateLock(dto.getPeriod(), dto.getLocked(), operatorId);
        return Result.success();
    }
}
