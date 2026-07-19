package com.company.hrms.module.attendance.controller;

import com.company.hrms.attendance.entity.LeaveApplication;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.module.attendance.dto.LeaveApplicationDTO;
import com.company.hrms.module.attendance.dto.LeaveApplicationVO;
import com.company.hrms.module.attendance.service.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 请假门户代理 Controller
 * 提供 /profile/leave/* 接口，强制 @DataScope(SELF)，仅限本人数据
 */
@RestController
@RequiredArgsConstructor
public class LeaveProfileController {

    private final LeaveService leaveService;

    /**
     * 本人请假列表
     * GET /api/v1/profile/leave/applications?page=1&leaveType=&status=
     */
    @GetMapping("/profile/leave/applications")
    public Result<PageResult<LeaveApplicationVO>> list(PageParam pageParam,
                                                       @RequestParam(required = false) String leaveType,
                                                       @RequestParam(required = false) String status) {
        Long employeeId = SecurityUtils.getCurrentUser().getEmployeeId();
        return Result.success(leaveService.pageApplications(pageParam, leaveType, status, employeeId));
    }

    /**
     * 本人提交请假
     * POST /api/v1/profile/leave/applications
     */
    @PostMapping("/profile/leave/applications")
    public Result<Map<String, Object>> submit(@RequestBody LeaveApplicationDTO dto) {
        Long employeeId = SecurityUtils.getCurrentUser().getEmployeeId();
        LeaveApplication app = leaveService.submit(employeeId, dto);
        return Result.success(Map.of("id", app.getId(), "status", app.getStatus()));
    }

    /**
     * 本人撤销请假
     * PUT /api/v1/profile/leave/applications/{id}/cancel
     */
    @PutMapping("/profile/leave/applications/{id}/cancel")
    public Result<Map<String, String>> cancel(@PathVariable Long id) {
        leaveService.cancel(id, SecurityUtils.getCurrentUser().getEmployeeId());
        return Result.success(Map.of("status", "CANCELLED"));
    }
}
