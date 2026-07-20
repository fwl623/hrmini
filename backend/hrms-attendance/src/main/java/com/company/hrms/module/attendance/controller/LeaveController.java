package com.company.hrms.module.attendance.controller;

import com.company.hrms.attendance.entity.LeaveApplication;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.module.attendance.dto.CalcDaysVO;
import com.company.hrms.module.attendance.dto.LeaveApplicationDTO;
import com.company.hrms.module.attendance.dto.LeaveApplicationVO;
import com.company.hrms.module.attendance.dto.LeaveBalanceVO;
import com.company.hrms.module.attendance.auth.AttendanceAccessGuard;
import com.company.hrms.module.attendance.service.LeaveService;
import jakarta.validation.Valid;
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
 * 请假管理 Controller
 */
@RestController
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    /**
     * 假期余额
     * GET /api/v1/leaves/balances?employeeId=1
     */
    @GetMapping("/leaves/balances")
    public Result<java.util.List<LeaveBalanceVO>> balances(@RequestParam(required = false) Long employeeId) {
        if (employeeId == null) {
            employeeId = SecurityUtils.getCurrentUser().getEmployeeId();
        }
        return Result.success(leaveService.getBalances(employeeId));
    }

    /**
     * 请假申请列表
     * GET /api/v1/leaves/applications?page=1&leaveType=&status=&employeeId=
     * 管理端传 employeeId=0 查全部；不传则查本人（门户）
     */
    @GetMapping("/leaves/applications")
    public Result<PageResult<LeaveApplicationVO>> list(PageParam pageParam,
                                                       @RequestParam(required = false) String leaveType,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) Long employeeId) {
        // employeeId=0 查全部（管理端）；不传则查本人（门户）
        if (employeeId == null) {
            employeeId = SecurityUtils.getCurrentUser().getEmployeeId();
            if (employeeId == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "当前账号未绑定员工，无法查询本人请假");
            }
        }
        if (employeeId == 0L) {
            AttendanceAccessGuard.requireHrStaff();
        }
        Long filterEmployeeId = employeeId == 0L ? null : employeeId;
        return Result.success(leaveService.pageApplications(pageParam, leaveType, status, filterEmployeeId));
    }

    /**
     * 提交请假
     * POST /api/v1/leaves/applications
     */
    @PostMapping("/leaves/applications")
    public Result<Map<String, Object>> submit(@Valid @RequestBody LeaveApplicationDTO dto) {
        AttendanceAccessGuard.requireHrStaff();
        Long employeeId = SecurityUtils.getCurrentUser().getEmployeeId();
        if (employeeId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "当前账号未绑定员工");
        }
        LeaveApplication app = leaveService.submit(employeeId, dto);
        return Result.success(Map.of("id", app.getId(), "status", app.getStatus()));
    }

    /**
     * 预览请假天数
     * GET /api/v1/leaves/calc-days?startTime=&endTime=
     */
    @GetMapping("/leaves/calc-days")
    public Result<CalcDaysVO> calcDays(@RequestParam String startTime,
                                       @RequestParam String endTime) {
        return Result.success(leaveService.calcDays(startTime, endTime));
    }

    /**
     * 撤销请假（管理端）
     * PUT /api/v1/leaves/applications/{id}/cancel
     */
    @PutMapping("/leaves/applications/{id}/cancel")
    public Result<Map<String, String>> cancel(@PathVariable Long id) {
        // 管理端代撤：不校验归属；审批撤回由 HR/管理员权限放行（BUG-025）
        AttendanceAccessGuard.requireHrStaff();
        leaveService.cancel(id);
        return Result.success(Map.of("status", "CANCELLED"));
    }
}
