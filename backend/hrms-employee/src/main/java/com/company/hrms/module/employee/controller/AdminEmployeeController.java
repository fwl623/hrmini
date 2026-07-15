package com.company.hrms.module.employee.controller;

import com.company.hrms.common.annotation.DataScope;
import com.company.hrms.common.dto.PageResult;
import com.company.hrms.common.dto.Result;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.module.employee.dto.EmployeePageQuery;
import com.company.hrms.module.employee.dto.EmployeeUpdateDTO;
import com.company.hrms.module.employee.dto.SalaryProfileUpdateDTO;
import com.company.hrms.module.employee.service.EmployeeService;
import com.company.hrms.module.employee.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 员工档案 — HR管理端接口
 * Base URL: /api/v1/employees
 *
 * 接口清单（对齐 API Contract v1.2.0 §6.3）：
 *   GET    /employees                        花名册分页+高级搜索
 *   GET    /employees/{id}                   员工详情
 *   PUT    /employees/{id}                   编辑（白名单字段）
 *   GET    /employees/{id}/salary            薪资档案
 *   PUT    /employees/{id}/salary            更新薪资档案
 *   GET    /employees/{id}/sensitive/{field} 敏感字段（二次验证）
 *   GET    /employees/mobile-change-applications 手机号变更待办
 *   GET    /employees/{id}/transfer-history  调岗历史
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
@Tag(name = "AdminEmployee", description = "HR管理端 — 员工档案接口")
public class AdminEmployeeController {

    private final EmployeeService employeeService;

    /**
     * GET /api/v1/employees
     * 花名册分页+高级搜索
     *
     * 查询参数: keyword, departmentIds, positionIds, employmentStatus,
     *           gradeLevels, hireDateFrom, hireDateTo, page, pageSize
     * 响应: 分页结构，每项含 employeeId, empNo, name, department, position,
     *       grade, employmentStatus, hireDate
     */
    @GetMapping
    @Operation(summary = "花名册分页+高级搜索")
    @DataScope
    public Result<PageResult<EmployeeListVO>> pageSearch(@Valid EmployeePageQuery query) {
        PageResult<EmployeeListVO> result = employeeService.pageSearch(query);
        return Result.success(result);
    }

    /**
     * GET /api/v1/employees/{id}
     * 员工详情，自动裁剪无权限敏感薪资/身份证字段
     *
     * 约束: fieldPermissions 由 FieldPermissionFilter 后置处理
     *       SYS_ADMIN 访问薪资字段返回 20002
     */
    @GetMapping("/{id}")
    @Operation(summary = "员工详情")
    public Result<EmployeeDetailVO> getDetail(@PathVariable Long id) {
        EmployeeDetailVO detail = employeeService.getDetail(id);
        return Result.success(detail);
    }

    /**
     * PUT /api/v1/employees/{id}
     * 编辑员工（白名单字段）
     *
     * 约束: 禁止修改 mobile, departmentId, positionId
     *       - mobile 变更须走 MOBILE_CHANGE 审批流程
     *       - departmentId/positionId 变更须走 POST /transfers 调岗流程
     * 白名单: name, gender, email, birthday, address, emergencyContact,
     *         emergencyPhone, workLocation
     */
    @PutMapping("/{id}")
    @Operation(summary = "编辑员工（白名单字段）")
    public Result<Void> update(@PathVariable Long id,
                                @Valid @RequestBody EmployeeUpdateDTO dto) {
        employeeService.update(id, dto);
        return Result.success();
    }

    /**
     * GET /api/v1/employees/{id}/salary
     * 薪资档案
     *
     * 约束: SYS_ADMIN 角色直接屏蔽薪资数据，返回 20002
     *       （由权限拦截器处理）
     */
    @GetMapping("/{id}/salary")
    @Operation(summary = "薪资档案")
    public Result<SalaryProfileVO> getSalary(@PathVariable Long id) {
        SalaryProfileVO profile = employeeService.getSalaryProfile(id);
        return Result.success(profile);
    }

    /**
     * PUT /api/v1/employees/{id}/salary
     * 更新薪资档案
     */
    @PutMapping("/{id}/salary")
    @Operation(summary = "更新薪资档案")
    public Result<Void> updateSalary(@PathVariable Long id,
                                      @Valid @RequestBody SalaryProfileUpdateDTO dto) {
        employeeService.updateSalaryProfile(id, dto);
        return Result.success();
    }

    /**
     * GET /api/v1/employees/{id}/sensitive/{field}
     * 敏感字段二次校验
     *
     * 约束: 无权限返回 20003
     *       查看记录记入 operation_log
     * 支持字段: idNumber, bankAccount
     */
    @GetMapping("/{id}/sensitive/{field}")
    @Operation(summary = "敏感字段查看（二次验证）")
    public Result<EmployeeSensitiveFieldVO> getSensitiveField(
            @PathVariable Long id,
            @PathVariable String field) {
        EmployeeSensitiveFieldVO vo = employeeService.getSensitiveField(id, field);
        return Result.success(vo);
    }

    /**
     * GET /api/v1/employees/mobile-change-applications
     * HR端手机号变更申请列表
     */
    @GetMapping("/mobile-change-applications")
    @Operation(summary = "HR端手机号变更待办列表")
    public Result<List<MobileChangeAppVO>> listMobileChangeApps() {
        List<MobileChangeAppVO> list = employeeService.listMobileChangeApplications();
        return Result.success(list);
    }

    /**
     * GET /api/v1/employees/{id}/transfer-history
     * 员工调岗历史
     *
     * 关联表: employee_transfer_history
     */
    @GetMapping("/{id}/transfer-history")
    @Operation(summary = "员工调岗历史")
    public Result<List<EmployeeTransferHistoryVO>> getTransferHistory(@PathVariable Long id) {
        List<EmployeeTransferHistoryVO> history = employeeService.getTransferHistory(id);
        return Result.success(history);
    }
}
