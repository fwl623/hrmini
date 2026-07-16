package com.company.hrms.employee.controller;

import com.company.hrms.common.datascope.DataScope;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.employee.dto.EmployeePageQuery;
import com.company.hrms.employee.dto.EmployeeUpdateDTO;
import com.company.hrms.employee.dto.SalaryProfileUpdateDTO;
import com.company.hrms.employee.entity.EmployeeTransferHistory;
import com.company.hrms.employee.entity.EmployeeMobileChangeApplication;
import com.company.hrms.employee.mapper.EmployeeMobileChangeApplicationMapper;
import com.company.hrms.employee.mapper.EmployeeTransferHistoryMapper;
import com.company.hrms.employee.service.EmployeeService;
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
 *       SYS_ADMIN 薪资接口 → 403（权限拦截器）
 */
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final SensitiveFieldService sensitiveFieldService;
    private final SalaryService salaryService;
    private final EmployeeTransferHistoryMapper transferHistoryMapper;
    private final EmployeeMobileChangeApplicationMapper mobileChangeMapper;

    /**
     * GET /api/v1/employees
     * 花名册分页+高级搜索
     */
    @GetMapping
    @DataScope
    public Result<PageResult<EmployeeListVO>> pageSearch(@Valid EmployeePageQuery query) {
        return Result.success(employeeService.pageSearch(query));
    }

    /**
     * GET /api/v1/employees/{id}
     * 员工详情（敏感字段由 FieldPermissionFilter 脱敏）
     */
    @GetMapping("/{id}")
    public Result<EmployeeDetailVO> getDetail(@PathVariable Long id) {
        return Result.success(employeeService.getDetail(id));
    }

    /**
     * PUT /api/v1/employees/{id}
     * 编辑员工（白名单字段）
     *
     * 含流程字段（departmentId/positionId/mobile/idNumber等）→ 20003
     * 已离职 → 30003
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                                @Valid @RequestBody EmployeeUpdateDTO dto) {
        employeeService.update(id, dto);
        return Result.success();
    }

    /**
     * GET /api/v1/employees/{id}/salary
     * 薪资档案查看
     * SYS_ADMIN → 403 | 无薪资档案 → 50003
     */
    @GetMapping("/{id}/salary")
    public Result<SalaryProfileVO> getSalary(@PathVariable Long id) {
        return Result.success(salaryService.getProfile(id));
    }

    /**
     * PUT /api/v1/employees/{id}/salary
     * 薪资档案更新 + 记录调薪历史
     */
    @PutMapping("/{id}/salary")
    public Result<Void> updateSalary(@PathVariable Long id,
                                      @Valid @RequestBody SalaryProfileUpdateDTO dto) {
        salaryService.updateProfile(id, dto);
        return Result.success();
    }

    /**
     * GET /api/v1/employees/{id}/sensitive/{field}
     * 敏感字段查看（密码二次验证 + AES解密 + 审计日志）
     *
     * 请求头 X-Sensitive-Password
     * 支持: idNumber, bankAccount
     * 无权限 → 20003 | 不支持字段 → 10001
     */
    @GetMapping("/{id}/sensitive/{field}")
    public Result<SensitiveFieldVO> getSensitiveField(
            @PathVariable Long id,
            @PathVariable String field,
            @RequestHeader("X-Sensitive-Password") String password) {
        return Result.success(sensitiveFieldService.reveal(id, field, password));
    }

    /**
     * GET /api/v1/employees/{id}/transfer-history
     * 调岗历史（时间倒序）
     */
    @GetMapping("/{id}/transfer-history")
    public Result<List<EmployeeTransferHistory>> getTransferHistory(@PathVariable Long id) {
        return Result.success(transferHistoryMapper.selectByEmployeeId(id));
    }

    /**
     * GET /api/v1/employees/mobile-change-applications
     * HR 手机号变更待办
     */
    @GetMapping("/mobile-change-applications")
    public Result<List<EmployeeMobileChangeApplication>> listMobileChangeApps() {
        return Result.success(mobileChangeMapper.selectPending());
    }
}
