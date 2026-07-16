package com.company.hrms.module.attendance.controller;

import com.company.hrms.common.web.Result;
import com.company.hrms.module.attendance.dto.DepartmentStatisticsVO;
import com.company.hrms.module.attendance.dto.PersonalStatisticsVO;
import com.company.hrms.module.attendance.service.SummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
     */
    @GetMapping("/personal")
    public Result<PersonalStatisticsVO> personal(@RequestParam Long employeeId,
                                                  @RequestParam String period) {
        return Result.success(summaryService.getPersonalStatistics(employeeId, period));
    }

    /**
     * 部门统计
     * GET /api/v1/attendance/statistics/department?departmentId=1&period=2026-07
     */
    @GetMapping("/department")
    public Result<DepartmentStatisticsVO> department(@RequestParam Long departmentId,
                                                      @RequestParam String period) {
        return Result.success(summaryService.getDepartmentStatistics(departmentId, period));
    }
}
