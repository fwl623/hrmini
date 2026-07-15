package com.company.hrms.module.employee.portal.controller;

import com.company.hrms.common.annotation.DataScope;
import com.company.hrms.common.dto.Result;
import com.company.hrms.common.util.SecurityUtils;
import com.company.hrms.module.employee.dto.*;
import com.company.hrms.module.employee.service.PortalEmployeeService;
import com.company.hrms.module.employee.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 员工门户 — 个人中心接口
 * Base URL: /api/v1/profile
 *
 * 全部接口强制 @DataScope(SELF)，仅允许操作本人数据
 *
 * 接口清单（对齐 API Contract v1.2.0 §6.8）：
 *   GET/PUT  /profile/me                              个人档案
 *   POST     /profile/mobile-change-applications        手机号变更申请
 *   GET      /profile/mobile-change-applications        本人变更记录
 *   POST     /profile/mobile-change-applications/{id}/cancel  撤销申请
 *   GET/POST /profile/resignation-requests              离职申请
 *   POST     /profile/resignation-requests/{id}/cancel  撤销离职申请
 *   PUT      /profile/security/password                 修改密码
 *   POST     /profile/security/mobile/bind              绑定手机
 *   DELETE   /profile/security/mobile                   解绑手机
 *   GET      /profile/security/login-logs               本人登录日志
 *   GET      /profile/payslips                          工资条列表
 *   GET      /profile/payslips/trend                    实发趋势
 *   GET      /profile/payslips/{period}                 工资条详情
 *   GET      /profile/payslips/{period}/pdf             工资条PDF下载
 *   POST     /profile/payslips/verify                   工资条二次验证
 *   GET/POST /profile/leave/applications                请假申请/记录
 *   POST     /profile/leave/applications/{id}/cancel    撤销请假（门户）
 *   GET      /profile/leave/balances                    假期余额
 *   POST     /profile/attendance/punch                  打卡
 *   POST     /profile/attendance/punch-fix              补卡
 *   GET      /profile/attendance/calendar               考勤日历
 *   GET/POST /profile/overtime/applications             加班申请/记录
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Tag(name = "PortalEmployee", description = "员工门户 — 个人中心接口")
@DataScope("SELF")
public class PortalEmployeeController {

    private final PortalEmployeeService portalEmployeeService;

    // ==================== 个人档案 ====================

    /**
     * GET /api/v1/profile/me
     * 本人档案详情（脱敏）
     *
     * 响应含 editableFields 白名单提示
     */
    @GetMapping("/me")
    @Operation(summary = "本人档案详情")
    public Result<ProfileVO> getMyProfile() {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        return Result.success(portalEmployeeService.getMyProfile(employeeId));
    }

    /**
     * PUT /api/v1/profile/me
     * 编辑本人档案
     *
     * 白名单: email, residenceAddress, emergencyContact, emergencyPhone
     * 禁止: mobile（须走 MOBILE_CHANGE 审批）、department、position、salary
     */
    @PutMapping("/me")
    @Operation(summary = "编辑本人档案（白名单字段）")
    public Result<Void> updateMyProfile(@Valid @RequestBody ProfileUpdateDTO dto) {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        portalEmployeeService.updateMyProfile(employeeId, dto);
        return Result.success();
    }

    // ==================== 手机号变更 ====================

    /**
     * POST /api/v1/profile/mobile-change-applications
     * 发起手机号变更申请（走 MOBILE_CHANGE 审批）
     */
    @PostMapping("/mobile-change-applications")
    @Operation(summary = "发起手机号变更申请")
    public Result<Void> applyMobileChange(@Valid @RequestBody MobileChangeApplyDTO dto) {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        Long userId = SecurityUtils.getCurrentUserId();
        portalEmployeeService.applyMobileChange(employeeId, userId, dto);
        return Result.success();
    }

    /**
     * GET /api/v1/profile/mobile-change-applications
     * 本人手机号变更申请记录
     */
    @GetMapping("/mobile-change-applications")
    @Operation(summary = "本人手机号变更记录")
    public Result<List<MobileChangeAppVO>> listMyMobileChanges() {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        return Result.success(portalEmployeeService.listMyMobileChanges(employeeId));
    }

    /**
     * POST /api/v1/profile/mobile-change-applications/{id}/cancel
     * 撤销手机号变更申请（仅 PENDING 可撤销）
     */
    @PostMapping("/mobile-change-applications/{id}/cancel")
    @Operation(summary = "撤销手机号变更申请")
    public Result<Void> cancelMobileChange(@PathVariable Long id) {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        portalEmployeeService.cancelMobileChange(employeeId, id);
        return Result.success();
    }

    // ==================== 离职申请 ====================

    /**
     * POST /api/v1/profile/resignation-requests
     * 员工发起离职申请
     */
    @PostMapping("/resignation-requests")
    @Operation(summary = "发起离职申请")
    public Result<Void> applyResignation(@Valid @RequestBody ResignationRequestDTO dto) {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        portalEmployeeService.applyResignation(employeeId, dto);
        return Result.success();
    }

    /**
     * GET /api/v1/profile/resignation-requests
     * 本人离职申请记录
     */
    @GetMapping("/resignation-requests")
    @Operation(summary = "本人离职申请记录")
    public Result<List<?>> listMyResignations() {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        return Result.success(portalEmployeeService.listMyResignations(employeeId));
    }

    /**
     * POST /api/v1/profile/resignation-requests/{id}/cancel
     * 撤销离职申请（仅 PENDING 可撤销）
     */
    @PostMapping("/resignation-requests/{id}/cancel")
    @Operation(summary = "撤销离职申请")
    public Result<Void> cancelResignation(@PathVariable Long id) {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        portalEmployeeService.cancelResignation(employeeId, id);
        return Result.success();
    }

    // ==================== 账号安全 ====================

    /**
     * PUT /api/v1/profile/security/password
     * 修改密码
     */
    @PutMapping("/security/password")
    @Operation(summary = "修改密码")
    public Result<Void> changePassword(@Valid @RequestBody PasswordChangeDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        portalEmployeeService.changePassword(userId, dto);
        return Result.success();
    }

    /**
     * POST /api/v1/profile/security/mobile/bind
     * 首次绑定手机号
     */
    @PostMapping("/security/mobile/bind")
    @Operation(summary = "绑定手机号")
    public Result<Void> bindMobile(@Valid @RequestBody MobileBindDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        portalEmployeeService.bindMobile(userId, dto);
        return Result.success();
    }

    /**
     * DELETE /api/v1/profile/security/mobile
     * 解绑手机号（需短信验证）
     */
    @DeleteMapping("/security/mobile")
    @Operation(summary = "解绑手机号")
    public Result<Void> unbindMobile(@RequestBody MobileBindDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        portalEmployeeService.unbindMobile(userId, dto);
        return Result.success();
    }

    /**
     * GET /api/v1/profile/security/login-logs
     * 本人登录日志
     */
    @GetMapping("/security/login-logs")
    @Operation(summary = "本人登录日志")
    public Result<List<LoginLogVO>> listMyLoginLogs() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(portalEmployeeService.listMyLoginLogs(userId));
    }

    // ==================== 工资条 ====================

    /**
     * GET /api/v1/profile/payslips
     * 工资条列表（无需二次验证）
     */
    @GetMapping("/payslips")
    @Operation(summary = "工资条列表")
    public Result<List<PayslipListVO>> listMyPayslips() {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        return Result.success(portalEmployeeService.listMyPayslips(employeeId));
    }

    /**
     * GET /api/v1/profile/payslips/trend
     * 近6月实发趋势
     */
    @GetMapping("/payslips/trend")
    @Operation(summary = "近6月实发趋势")
    public Result<List<PayslipTrendVO>> getPayslipTrend() {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        return Result.success(portalEmployeeService.getPayslipTrend(employeeId));
    }

    /**
     * GET /api/v1/profile/payslips/{period}
     * 工资条详情（须先通过二次验证，否则 60004）
     */
    @GetMapping("/payslips/{period}")
    @Operation(summary = "工资条详情（须先验证）")
    public Result<PayslipDetailVO> getPayslipDetail(@PathVariable String period) {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        PayslipDetailVO detail = portalEmployeeService.getPayslipDetail(employeeId, period);
        return Result.success(detail);
    }

    /**
     * GET /api/v1/profile/payslips/{period}/pdf
     * 工资条 PDF 下载（须先通过二次验证）
     */
    @GetMapping("/payslips/{period}/pdf")
    @Operation(summary = "工资条PDF下载")
    public Result<byte[]> getPayslipPdf(@PathVariable String period) {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        byte[] pdf = portalEmployeeService.getPayslipPdf(employeeId, period);
        return Result.success(pdf);
    }

    /**
     * POST /api/v1/profile/payslips/verify
     * 工资条二次验证
     *
     * 成功后 Redis 写入 hrms:payslip:verified:{userId} TTL 30min
     */
    @PostMapping("/payslips/verify")
    @Operation(summary = "工资条二次验证")
    public Result<Void> verifyPayslip(@Valid @RequestBody PayslipVerifyDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        portalEmployeeService.verifyPayslip(userId, dto.getVerifyType(), dto.getVerifyCode());
        return Result.success();
    }

    // ==================== 请假代理（转发至 hrms-attendance） ====================

    /**
     * GET /api/v1/profile/leave/applications
     * 本人请假记录
     */
    @GetMapping("/leave/applications")
    @Operation(summary = "本人请假记录")
    public Result<List<?>> listMyLeaves() {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        return Result.success(portalEmployeeService.listMyLeaves(employeeId));
    }

    /**
     * POST /api/v1/profile/leave/applications
     * 提交请假申请
     */
    @PostMapping("/leave/applications")
    @Operation(summary = "提交请假申请")
    public Result<Object> applyLeave(@RequestBody Object dto) {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        return Result.success(portalEmployeeService.applyLeave(employeeId, dto));
    }

    /**
     * POST /api/v1/profile/leave/applications/{id}/cancel
     * 撤销请假（门户端，与管理端 PUT 区分）
     */
    @PostMapping("/leave/applications/{id}/cancel")
    @Operation(summary = "撤销请假申请（门户）")
    public Result<Void> cancelLeave(@PathVariable Long id) {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        portalEmployeeService.cancelLeave(employeeId, id);
        return Result.success();
    }

    /**
     * GET /api/v1/profile/leave/balances
     * 本人假期余额
     */
    @GetMapping("/leave/balances")
    @Operation(summary = "假期余额")
    public Result<?> getLeaveBalances() {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        // 代理至 hrms-attendance
        return Result.success(null);
    }

    // ==================== 考勤代理 ====================

    /**
     * POST /api/v1/profile/attendance/punch
     * 打卡（代理至 /attendance/punch）
     */
    @PostMapping("/attendance/punch")
    @Operation(summary = "打卡")
    public Result<Object> punch(@RequestBody Object dto) {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        return Result.success(portalEmployeeService.punch(employeeId, dto));
    }

    /**
     * POST /api/v1/profile/attendance/punch-fix
     * 补卡申请（代理至 /attendance/punch-fix）
     */
    @PostMapping("/attendance/punch-fix")
    @Operation(summary = "补卡申请")
    public Result<Object> applyPunchFix(@RequestBody Object dto) {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        return Result.success(portalEmployeeService.applyPunchFix(employeeId, dto));
    }

    /**
     * GET /api/v1/profile/attendance/calendar
     * 考勤日历
     */
    @GetMapping("/attendance/calendar")
    @Operation(summary = "考勤日历")
    public Result<?> getAttendanceCalendar(@RequestParam(required = false) String period) {
        // 代理至 hrms-attendance
        return Result.success(null);
    }

    // ==================== 加班代理 ====================

    /**
     * GET /api/v1/profile/overtime/applications
     * 本人加班记录
     */
    @GetMapping("/overtime/applications")
    @Operation(summary = "本人加班记录")
    public Result<List<?>> listMyOvertimes() {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        return Result.success(portalEmployeeService.listMyOvertimes(employeeId));
    }

    /**
     * POST /api/v1/profile/overtime/applications
     * 提交加班申请
     */
    @PostMapping("/overtime/applications")
    @Operation(summary = "提交加班申请")
    public Result<Object> applyOvertime(@RequestBody Object dto) {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        return Result.success(portalEmployeeService.applyOvertime(employeeId, dto));
    }
}
