package com.company.hrms.module.attendance.controller;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.security.LoginUser;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.Result;
import com.company.hrms.module.attendance.dto.DepartmentStatisticsVO;
import com.company.hrms.module.attendance.dto.PersonalStatisticsVO;
import com.company.hrms.module.attendance.auth.AttendanceAccessGuard;
import com.company.hrms.module.attendance.service.SummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/**
 * 考勤统计 Controller
 */
@RestController
@RequestMapping("/attendance/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final SummaryService summaryService;

    /**
     * 个人统计
     * GET /api/v1/attendance/statistics/personal?employeeId=1&period=2026-07
     * 兼容查询参数 month（与 period 同义）
     */
    @GetMapping("/personal")
    public Result<PersonalStatisticsVO> personal(@RequestParam Long employeeId,
                                                  @RequestParam(required = false) String period,
                                                  @RequestParam(required = false) String month) {
        AttendanceAccessGuard.requireHrOrDeptMgr();
        return Result.success(summaryService.getPersonalStatistics(employeeId, resolvePeriod(period, month)));
    }

    /**
     * 部门统计
     * GET /api/v1/attendance/statistics/department?departmentId=1&period=2026-07
     * departmentId / period 可省略：部门主管默认本部门 + 当月
     */
    @GetMapping("/department")
    public Result<DepartmentStatisticsVO> department(@RequestParam(required = false) Long departmentId,
                                                      @RequestParam(required = false) String period,
                                                      @RequestParam(required = false) String month) {
        AttendanceAccessGuard.requireHrOrDeptMgr();
        Long deptId = departmentId;
        if (deptId == null) {
            LoginUser user = SecurityUtils.requireLoginUser();
            deptId = user.getDeptId();
            if (deptId == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "请指定 departmentId");
            }
        }
        return Result.success(summaryService.getDepartmentStatistics(deptId, resolvePeriod(period, month)));
    }

    private static String resolvePeriod(String period, String month) {
        String p = (period != null && !period.isBlank()) ? period.trim()
                : (month != null && !month.isBlank() ? month.trim() : null);
        if (p == null) {
            return YearMonth.now().toString();
        }
        return p;
    }
}
