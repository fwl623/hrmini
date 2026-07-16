package com.company.hrms.module.attendance.controller;

import com.company.hrms.attendance.entity.OvertimeApplication;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.module.attendance.dto.OvertimeApplicationDTO;
import com.company.hrms.module.attendance.dto.OvertimeApplicationVO;
import com.company.hrms.module.attendance.service.OvertimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 加班管理 Controller
 */
@RestController
@RequestMapping("/overtime")
@RequiredArgsConstructor
public class OvertimeController {

    private final OvertimeService overtimeService;

    /**
     * 加班列表
     * GET /api/v1/overtime/applications?page=1
     */
    @GetMapping("/applications")
    public Result<PageResult<OvertimeApplicationVO>> list(PageParam pageParam) {
        Long employeeId = SecurityUtils.getCurrentUser().getEmployeeId();
        return Result.success(overtimeService.pageApplications(pageParam, employeeId));
    }

    /**
     * 提交加班
     * POST /api/v1/overtime/applications
     */
    @PostMapping("/applications")
    public Result<Map<String, Object>> submit(@RequestBody OvertimeApplicationDTO dto) {
        Long employeeId = SecurityUtils.getCurrentUser().getEmployeeId();
        OvertimeApplication app = overtimeService.submit(employeeId, dto);
        return Result.success(Map.of("id", app.getId(), "status", app.getStatus()));
    }
}
