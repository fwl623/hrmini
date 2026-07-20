package com.company.hrms.employee.controller;

import com.company.hrms.common.datascope.DataScope;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.Result;
import com.company.hrms.employee.dto.MobileBindDTO;
import com.company.hrms.employee.dto.MobileChangeApplyDTO;
import com.company.hrms.employee.dto.PasswordChangeDTO;
import com.company.hrms.employee.dto.ProfileUpdateDTO;
import com.company.hrms.employee.entity.EmployeeMobileChangeApplication;
import com.company.hrms.employee.service.EmployeeService;
import com.company.hrms.employee.service.MobileChangeService;
import com.company.hrms.employee.vo.LoginLogVO;
import com.company.hrms.employee.vo.ProfileVO;
import com.company.hrms.employee.vo.TransferHistoryVO;
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
@RequestMapping("/profile")
@RequiredArgsConstructor
@DataScope(SELF)
public class ProfileController {

    private final EmployeeService employeeService;
    private final MobileChangeService mobileChangeService;

    // ==================== 我的档案 ====================

    @GetMapping("/me")
    public Result<ProfileVO> getMyProfile() {
        return Result.success(employeeService.getMyProfile(SecurityUtils.getEmployeeId()));
    }

    @PutMapping("/me")
    public Result<Void> updateMyProfile(@Valid @RequestBody ProfileUpdateDTO dto) {
        employeeService.updateMyProfile(SecurityUtils.getEmployeeId(), dto);
        return Result.success();
    }

    // ==================== 手机号变更（提交流程：提交→审批→同步auth） ====================

    /**
     * POST /api/v1/profile/mobile-change-applications
     * 提交手机号变更申请
     *
     * 流程：创建记录 → 发起 MOBILE_CHANGE 审批（依赖 C 组） → 审批通过后同步 auth（依赖 A 组）
     */
    @PostMapping("/mobile-change-applications")
    public Result<Void> applyMobileChange(@Valid @RequestBody MobileChangeApplyDTO dto) {
        mobileChangeService.apply(SecurityUtils.getEmployeeId(), SecurityUtils.getUserId(), dto);
        return Result.success();
    }

    /** GET /api/v1/profile/mobile-change-applications — 本人申请记录 */
    @GetMapping("/mobile-change-applications")
    public Result<List<EmployeeMobileChangeApplication>> listMyMobileChanges() {
        return Result.success(mobileChangeService.listMyApplications(SecurityUtils.getEmployeeId()));
    }

    /** POST /api/v1/profile/mobile-change-applications/{id}/cancel — 撤销（仅 PENDING） */
    @PostMapping("/mobile-change-applications/{id}/cancel")
    public Result<Void> cancelMobileChange(@PathVariable Long id) {
        mobileChangeService.cancel(SecurityUtils.getEmployeeId(), id);
        return Result.success();
    }

    // ==================== 账号安全 ====================

    @PutMapping("/security/password")
    public Result<Void> changePassword(@Valid @RequestBody PasswordChangeDTO dto,
                                       jakarta.servlet.http.HttpServletRequest request) {
        employeeService.changePassword(SecurityUtils.getUserId(), dto, extractBearer(request));
        return Result.success();
    }

    /**
     * 首次绑定手机号。已绑定则拒绝，请走 POST /profile/mobile-change-applications。
     */
    @PostMapping("/security/mobile/bind")
    public Result<Void> bindMobile(@Valid @RequestBody MobileBindDTO dto) {
        employeeService.bindMobile(SecurityUtils.getEmployeeId(), SecurityUtils.getUserId(), dto);
        return Result.success();
    }

    @GetMapping("/security/login-logs")
    public Result<List<LoginLogVO>> listLoginLogs() {
        return Result.success(employeeService.listLoginLogs(SecurityUtils.getUserId()));
    }

    /** GET /api/v1/profile/transfer-history — 本人调岗历史 */
    @GetMapping("/transfer-history")
    public Result<List<TransferHistoryVO>> listMyTransferHistory() {
        return Result.success(employeeService.listTransferHistory(SecurityUtils.getEmployeeId()));
    }

    private static String extractBearer(jakarta.servlet.http.HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }
}
