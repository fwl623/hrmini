package com.company.hrms.employee.controller;

import com.company.hrms.common.datascope.DataScope;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.Result;
import com.company.hrms.employee.dto.MobileBindDTO;
import com.company.hrms.employee.dto.MobileChangeApplyDTO;
import com.company.hrms.employee.dto.PasswordChangeDTO;
import com.company.hrms.employee.dto.ProfileUpdateDTO;
import com.company.hrms.employee.entity.EmployeeMobileChangeApplication;
import com.company.hrms.employee.mapper.EmployeeMobileChangeApplicationMapper;
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
    private final EmployeeMobileChangeApplicationMapper mobileChangeMapper;

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
     * 编辑本人档案（白名单）
     */
    @PutMapping("/me")
    public Result<Void> updateMyProfile(@Valid @RequestBody ProfileUpdateDTO dto) {
        Long employeeId = SecurityUtils.getEmployeeId();
        employeeService.updateMyProfile(employeeId, dto);
        return Result.success();
    }

    // ==================== 手机号变更 ====================

    /**
     * POST /api/v1/profile/mobile-change-applications
     * 提交手机号变更申请（走 MOBILE_CHANGE 审批）
     */
    @PostMapping("/mobile-change-applications")
    public Result<Void> applyMobileChange(@Valid @RequestBody MobileChangeApplyDTO dto) {
        Long employeeId = SecurityUtils.getEmployeeId();
        Long userId = SecurityUtils.getUserId();
        // 创建申请记录，状态 PENDING
        EmployeeMobileChangeApplication app = new EmployeeMobileChangeApplication();
        app.setEmployeeId(employeeId);
        app.setUserId(userId);
        app.setNewMobile(dto.getNewMobile());
        app.setReason(dto.getReason());
        app.setSmsVerified(1);
        app.setStatus("PENDING");
        mobileChangeMapper.insert(app);
        return Result.success();
    }

    /**
     * GET /api/v1/profile/mobile-change-applications
     * 本人手机号变更申请记录
     */
    @GetMapping("/mobile-change-applications")
    public Result<List<EmployeeMobileChangeApplication>> listMyMobileChanges() {
        Long employeeId = SecurityUtils.getEmployeeId();
        return Result.success(mobileChangeMapper.selectByEmployeeId(employeeId));
    }

    /**
     * POST /api/v1/profile/mobile-change-applications/{id}/cancel
     * 撤销手机号变更申请（仅 PENDING 可撤销）
     */
    @PostMapping("/mobile-change-applications/{id}/cancel")
    public Result<Void> cancelMobileChange(@PathVariable Long id) {
        Long employeeId = SecurityUtils.getEmployeeId();
        EmployeeMobileChangeApplication app = mobileChangeMapper.selectById(id);
        if (app == null || !app.getEmployeeId().equals(employeeId)) {
            return Result.error(404, "申请不存在");
        }
        if (!"PENDING".equals(app.getStatus())) {
            return Result.error(60002, "当前状态不允许撤销");
        }
        app.setStatus("CANCELLED");
        mobileChangeMapper.updateById(app);
        return Result.success();
    }

    // ==================== 账号安全 ====================

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
