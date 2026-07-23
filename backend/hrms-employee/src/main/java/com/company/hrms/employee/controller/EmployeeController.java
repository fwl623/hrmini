package com.company.hrms.employee.controller;

import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.common.datascope.DataScope;
import com.company.hrms.employee.auth.EmployeeAccessGuard;
import com.company.hrms.employee.dto.EmployeePageQuery;
import com.company.hrms.employee.dto.EmployeeUpdateDTO;
import com.company.hrms.employee.dto.SalaryProfileUpdateDTO;
import com.company.hrms.employee.entity.EmployeeMobileChangeApplication;
import com.company.hrms.employee.mapper.EmployeeMobileChangeApplicationMapper;
import com.company.hrms.employee.service.EmployeeService;
import com.company.hrms.employee.service.MobileChangeService;
import com.company.hrms.employee.service.SalaryService;
import com.company.hrms.employee.service.SensitiveFieldService;
import com.company.hrms.common.util.ExcelExportUtil;
import com.company.hrms.employee.vo.EmployeeDetailVO;
import com.company.hrms.employee.vo.EmployeeExportVO;
import com.company.hrms.employee.vo.EmployeeListVO;
import com.company.hrms.employee.vo.SalaryHistoryVO;
import com.company.hrms.employee.vo.SalaryProfileVO;
import com.company.hrms.employee.vo.SensitiveFieldVO;
import com.company.hrms.employee.vo.TransferHistoryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 员工档案管理接口
 * Base: /api/v1/employees
 *
 * 权限：HR_STAFF 全部 / DEPT_MANAGER 本部门 / EMPLOYEE 仅 SELF；
 * FINANCE / FINANCE_MANAGER 不可访问花名册（BUG-024）；薪资档案见 {@link EmployeeAccessGuard#requireSalaryAccess()}。
 */
@RestController
@RequestMapping("/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final SensitiveFieldService sensitiveFieldService;
    private final SalaryService salaryService;
    private final MobileChangeService mobileChangeService;
    private final EmployeeMobileChangeApplicationMapper mobileChangeMapper;

    @GetMapping
    @DataScope
    public Result<PageResult<EmployeeListVO>> pageSearch(@Valid EmployeePageQuery query) {
        EmployeeAccessGuard.requireRosterRead();
        return Result.success(employeeService.pageSearch(query));
    }

    @GetMapping("/export-excel")
    public ResponseEntity<byte[]> export(EmployeePageQuery query) {
        EmployeeAccessGuard.requireRosterRead();
        List<EmployeeExportVO> list = employeeService.exportList(query);
        byte[] bytes = ExcelExportUtil.generateExcelBytes("员工花名册", list, EmployeeExportVO.class);
        String fileName = URLEncoder.encode("员工花名册", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=utf-8''" + fileName + ".xlsx")
                .body(bytes);
    }

    @GetMapping("/{id}")
    @DataScope
    public Result<EmployeeDetailVO> getDetail(@PathVariable Long id) {
        EmployeeAccessGuard.requireRosterRead();
        return Result.success(employeeService.getDetail(id));
    }

    @PutMapping("/{id}")
    @DataScope
    public Result<Void> update(@PathVariable Long id,
                                @Valid @RequestBody EmployeeUpdateDTO dto) {
        EmployeeAccessGuard.requireRosterWrite();
        employeeService.update(id, dto);
        return Result.success();
    }

    @GetMapping("/{id}/salary")
    public Result<SalaryProfileVO> getSalary(@PathVariable Long id) {
        EmployeeAccessGuard.requireSalaryAccess();
        return Result.success(salaryService.getProfile(id));
    }

    @PutMapping("/{id}/salary")
    public Result<Void> updateSalary(@PathVariable Long id,
                                      @Valid @RequestBody SalaryProfileUpdateDTO dto) {
        EmployeeAccessGuard.requireSalaryAccess();
        salaryService.updateProfile(id, dto);
        return Result.success();
    }

    /** 调薪历史（与薪资档案同权限：HR_STAFF / FINANCE / FINANCE_MANAGER） */
    @GetMapping("/{id}/salary/history")
    public Result<List<SalaryHistoryVO>> getSalaryHistory(@PathVariable Long id) {
        return Result.success(salaryService.listHistory(id));
    }

    @GetMapping("/{id}/sensitive/{field}")
    public Result<SensitiveFieldVO> getSensitiveField(
            @PathVariable Long id,
            @PathVariable String field,
            @RequestHeader(value = "X-Sensitive-Password", required = false) String password) {
        EmployeeAccessGuard.requireRosterWrite();
        return Result.success(sensitiveFieldService.reveal(id, field, password));
    }

    @GetMapping("/{id}/transfer-history")
    public Result<List<TransferHistoryVO>> getTransferHistory(@PathVariable Long id) {
        EmployeeAccessGuard.requireRosterWrite();
        return Result.success(employeeService.listTransferHistory(id));
    }

    // ==================== 手机号变更 HR 待办 ====================

    /**
     * GET /api/v1/employees/mobile-change-applications
     * HR 手机号变更待办列表（筛选 PENDING）
     */
    @GetMapping("/mobile-change-applications")
    public Result<List<EmployeeMobileChangeApplication>> listMobileChangeApps() {
        EmployeeAccessGuard.requireHrStaff();
        return Result.success(mobileChangeService.listPending());
    }

    /**
     * POST /api/v1/employees/mobile-change-applications/{id}/approve
     * HR 审批通过手机号变更
     */
    @PostMapping("/mobile-change-applications/{id}/approve")
    public Result<Void> approveMobileChange(@PathVariable Long id) {
        EmployeeAccessGuard.requireHrStaff();
        mobileChangeService.approve(id);
        return Result.success();
    }

    /**
     * POST /api/v1/employees/mobile-change-applications/{id}/reject
     * HR 审批驳回手机号变更
     */
    @PostMapping("/mobile-change-applications/{id}/reject")
    public Result<Void> rejectMobileChange(@PathVariable Long id) {
        EmployeeAccessGuard.requireHrStaff();
        mobileChangeService.reject(id);
        return Result.success();
    }
}
