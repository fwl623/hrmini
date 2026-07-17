package com.company.hrms.module.attendance.controller;

import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.Result;
import com.company.hrms.module.attendance.dto.AttendanceCalendarVO;
import com.company.hrms.module.attendance.dto.PunchDTO;
import com.company.hrms.module.attendance.dto.PunchFixDTO;
import com.company.hrms.module.attendance.dto.QuotaVO;
import com.company.hrms.module.attendance.dto.TodayPunchVO;
import com.company.hrms.module.attendance.service.PunchService;
import com.company.hrms.module.attendance.service.SummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 考勤门户代理 Controller
 * 提供 /profile/attendance/* 接口，强制 @DataScope(SELF)，仅限本人数据
 */
@RestController
@RequiredArgsConstructor
public class AttendanceProfileController {

    private final PunchService punchService;
    private final SummaryService summaryService;

    /** 员工打卡（门户代理） */
    @PostMapping("/profile/attendance/punch")
    public Result<Map<String, String>> punch(@RequestBody PunchDTO dto) {
        Long employeeId = SecurityUtils.getCurrentUser().getEmployeeId();
        return Result.success(Map.of("punchStatus", punchService.punch(employeeId, dto)));
    }

    /** 员工补卡（门户代理） */
    @PostMapping("/profile/attendance/punch-fix")
    public Result<Map<String, Object>> applyFix(@RequestBody PunchFixDTO dto) {
        Long employeeId = SecurityUtils.getCurrentUser().getEmployeeId();
        return Result.success(punchService.applyFix(employeeId, dto));
    }

    /** 今日打卡状态（门户代理） */
    @GetMapping("/profile/attendance/punch/today")
    public Result<TodayPunchVO> todayStatus() {
        Long employeeId = SecurityUtils.getCurrentUser().getEmployeeId();
        return Result.success(punchService.getTodayStatus(employeeId));
    }

    /** 补卡配额（门户代理） */
    @GetMapping("/profile/attendance/punch-fix/quota")
    public Result<QuotaVO> fixQuota() {
        Long employeeId = SecurityUtils.getCurrentUser().getEmployeeId();
        return Result.success(punchService.getFixQuota(employeeId));
    }

    /** 考勤日历（门户代理） */
    @GetMapping("/profile/attendance/calendar")
    public Result<AttendanceCalendarVO> calendar(@RequestParam String period) {
        Long employeeId = SecurityUtils.getCurrentUser().getEmployeeId();
        return Result.success(summaryService.getCalendar(employeeId, period));
    }
}
