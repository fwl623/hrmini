package com.company.hrms.employee.controller;

import com.company.hrms.common.datascope.DataScope;
import com.company.hrms.common.enums.DataScopeType;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.Result;
import com.company.hrms.employee.dto.MobileBindDTO;
import com.company.hrms.employee.dto.PasswordChangeDTO;
import com.company.hrms.employee.dto.ProfileUpdateDTO;
import com.company.hrms.employee.service.EmployeeService;
import com.company.hrms.employee.vo.LoginLogVO;
import com.company.hrms.employee.vo.ProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.company.hrms.common.enums.DataScopeType.SELF;

/**
 * 个人中心接口（员工门户）
 * Base: /api/v1/profile
 *
 * 全部接口强制 @DataScope(SELF)
 */
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@DataScope(SELF)
public class ProfileController {

    private final EmployeeService employeeService;

    /**
     * GET /api/v1/profile/me
     * 本人档案（脱敏）
     */
    @GetMapping("/me")
    public Result<ProfileVO> getMyProfile() {
        Long employeeId = SecurityUtils.getEmployeeId();
        return Result.success(employeeService.getMyProfile(employeeId));
    }

    /**
     * PUT /api/v1/profile/me
     * 编辑本人档案
     *
     * 白名单: email, residenceAddress, emergencyContact, emergencyPhone
     */
    @PutMapping("/me")
    public Result<Void> updateMyProfile(@Valid @RequestBody ProfileUpdateDTO dto) {
        Long employeeId = SecurityUtils.getEmployeeId();
        employeeService.updateMyProfile(employeeId, dto);
        return Result.success();
    }

    /**
     * PUT /api/v1/profile/security/password
     * 修改密码
     */
    @PutMapping("/security/password")
    public Result<Void> changePassword(@Valid @RequestBody PasswordChangeDTO dto) {
        Long userId = SecurityUtils.getUserId();
        employeeService.changePassword(userId, dto);
        return Result.success();
    }

    /**
     * POST /api/v1/profile/security/mobile/bind
     * 首次绑定手机号
     */
    @PostMapping("/security/mobile/bind")
    public Result<Void> bindMobile(@Valid @RequestBody MobileBindDTO dto) {
        Long userId = SecurityUtils.getUserId();
        employeeService.bindMobile(userId, dto);
        return Result.success();
    }

    /**
     * GET /api/v1/profile/security/login-logs
     * 本人登录日志
     */
    @GetMapping("/security/login-logs")
    public Result<List<LoginLogVO>> listLoginLogs() {
        Long userId = SecurityUtils.getUserId();
        return Result.success(employeeService.listLoginLogs(userId));
    }
}
