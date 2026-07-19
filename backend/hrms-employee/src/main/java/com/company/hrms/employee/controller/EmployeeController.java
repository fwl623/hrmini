package com.company.hrms.employee.controller;

import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.common.datascope.DataScope;
import com.company.hrms.employee.dto.EmployeePageQuery;
import com.company.hrms.employee.dto.EmployeeUpdateDTO;
import com.company.hrms.employee.dto.SalaryProfileUpdateDTO;
import com.company.hrms.employee.entity.EmployeeMobileChangeApplication;
import com.company.hrms.employee.entity.EmployeeTransferHistory;
import com.company.hrms.employee.mapper.EmployeeMobileChangeApplicationMapper;
import com.company.hrms.employee.mapper.EmployeeTransferHistoryMapper;
import com.company.hrms.employee.service.EmployeeService;
import com.company.hrms.employee.service.MobileChangeService;
import com.company.hrms.employee.service.SalaryService;
import com.company.hrms.employee.service.SensitiveFieldService;
import com.company.hrms.employee.vo.EmployeeDetailVO;
import com.company.hrms.employee.vo.EmployeeListVO;
import com.company.hrms.employee.vo.SalaryProfileVO;
import com.company.hrms.employee.vo.SensitiveFieldVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 员工档案管理接口
 * Base: /api/v1/employees
 *
 * 权限：HR_STAFF 全部 / DEPT_MANAGER 本部门 / EMPLOYEE 仅 SELF
 */
@RestController
@RequestMapping("/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final SensitiveFieldService sensitiveFieldService;
    private final SalaryService salaryService;
    private final MobileChangeService mobileChangeService;
    private final EmployeeTransferHistoryMapper transferHistoryMapper;
    private final EmployeeMobileChangeApplicationMapper mobileChangeMapper;

    @GetMapping
    @DataScope
    public Result<PageResult<EmployeeListVO>> pageSearch(@Valid EmployeePageQuery query) {
        return Result.success(employeeService.pageSearch(query));
    }

    @GetMapping("/{id}")
    @DataScope
    public Result<EmployeeDetailVO> getDetail(@PathVariable Long id) {
        return Result.success(employeeService.getDetail(id));
    }

    @PutMapping("/{id}")
    @DataScope
    public Result<Void> update(@PathVariable Long id,
                                @Valid @RequestBody EmployeeUpdateDTO dto) {
        employeeService.update(id, dto);
        return Result.success();
    }

    @GetMapping("/{id}/salary")
    public Result<SalaryProfileVO> getSalary(@PathVariable Long id) {
        return Result.success(salaryService.getProfile(id));
    }

    @PutMapping("/{id}/salary")
    public Result<Void> updateSalary(@PathVariable Long id,
                                      @Valid @RequestBody SalaryProfileUpdateDTO dto) {
        salaryService.updateProfile(id, dto);
        return Result.success();
    }

    @GetMapping("/{id}/sensitive/{field}")
    public Result<SensitiveFieldVO> getSensitiveField(
            @PathVariable Long id,
            @PathVariable String field,
            @RequestHeader(value = "X-Sensitive-Password", required = false) String password) {
        return Result.success(sensitiveFieldService.reveal(id, field, password));
    }

    @GetMapping("/{id}/transfer-history")
    public Result<List<EmployeeTransferHistory>> getTransferHistory(@PathVariable Long id) {
        return Result.success(transferHistoryMapper.selectByEmployeeId(id));
    }

    // ==================== 手机号变更 HR 待办 ====================

    /**
     * GET /api/v1/employees/mobile-change-applications
     * HR 手机号变更待办列表（筛选 PENDING）
     */
    @GetMapping("/mobile-change-applications")
    public Result<List<EmployeeMobileChangeApplication>> listMobileChangeApps() {
        return Result.success(mobileChangeService.listPending());
    }

    /**
     * POST /api/v1/employees/mobile-change-applications/{id}/approve
     * HR 审批通过手机号变更
     */
    @PostMapping("/mobile-change-applications/{id}/approve")
    public Result<Void> approveMobileChange(@PathVariable Long id) {
        mobileChangeService.approve(id);
        return Result.success();
    }

    /**
     * POST /api/v1/employees/mobile-change-applications/{id}/reject
     * HR 审批驳回手机号变更
     */
    @PostMapping("/mobile-change-applications/{id}/reject")
    public Result<Void> rejectMobileChange(@PathVariable Long id) {
        mobileChangeService.reject(id);
        return Result.success();
    }
}
