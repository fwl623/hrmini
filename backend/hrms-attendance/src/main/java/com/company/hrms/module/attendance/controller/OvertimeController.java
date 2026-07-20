package com.company.hrms.module.attendance.controller;

import com.company.hrms.attendance.entity.OvertimeApplication;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.module.attendance.dto.OvertimeApplicationDTO;
import com.company.hrms.module.attendance.dto.OvertimeApplicationVO;
import com.company.hrms.module.attendance.auth.AttendanceAccessGuard;
import com.company.hrms.module.attendance.service.OvertimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
     * GET /api/v1/overtime/applications?page=1&employeeId=
     * 管理端传 employeeId=0 查全部；不传则查当前用户
     */
    @GetMapping("/applications")
    public Result<PageResult<OvertimeApplicationVO>> list(PageParam pageParam,
                                                          @RequestParam(required = false) Long employeeId) {
        // employeeId=0 查全部（管理端）；不传则查本人（门户）
        if (employeeId == null) {
            employeeId = SecurityUtils.getCurrentUser().getEmployeeId();
            if (employeeId == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "当前账号未绑定员工，无法查询本人加班");
            }
        }
        if (employeeId == 0L) {
            AttendanceAccessGuard.requireHrStaff();
        }
        Long filterEmployeeId = employeeId == 0L ? null : employeeId;
        return Result.success(overtimeService.pageApplications(pageParam, filterEmployeeId));
    }

    /**
     * 提交加班
     * POST /api/v1/overtime/applications
     */
    @PostMapping("/applications")
    public Result<Map<String, Object>> submit(@RequestBody OvertimeApplicationDTO dto) {
        AttendanceAccessGuard.requireEmployee();
        Long employeeId = SecurityUtils.getCurrentUser().getEmployeeId();
        if (employeeId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "当前账号未绑定员工");
        }
        OvertimeApplication app = overtimeService.submit(employeeId, dto);
        return Result.success(Map.of("id", app.getId(), "status", app.getStatus()));
    }
}
