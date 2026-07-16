package com.company.hrms.employee.controller;

import com.company.hrms.common.datascope.DataScope;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.employee.dto.EmployeePageQuery;
import com.company.hrms.employee.dto.EmployeeUpdateDTO;
import com.company.hrms.employee.service.EmployeeService;
import com.company.hrms.employee.vo.EmployeeDetailVO;
import com.company.hrms.employee.vo.EmployeeListVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 员工档案管理接口
 * Base: /api/v1/employees
 *
 * 权限：HR_STAFF 全部 / DEPT_MANAGER 本部门 / EMPLOYEE 仅 SELF
 */
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    /**
     * GET /api/v1/employees
     * 花名册分页+高级搜索
     *
     * 参数: keyword, departmentIds, positionIds, employmentStatus,
     *       gradeLevels, hireDateFrom, hireDateTo, page, pageSize
     */
    @GetMapping
    @DataScope
    public Result<PageResult<EmployeeListVO>> pageSearch(@Valid EmployeePageQuery query) {
        return Result.success(employeeService.pageSearch(query));
    }

    /**
     * GET /api/v1/employees/{id}
     * 员工详情
     *
     * - 密码字段由 FieldPermissionFilter 脱敏
     * - 薪资信息对 SYS_ADMIN 返回 20002（权限拦截器处理）
     */
    @GetMapping("/{id}")
    public Result<EmployeeDetailVO> getDetail(@PathVariable Long id) {
        return Result.success(employeeService.getDetail(id));
    }

    /**
     * PUT /api/v1/employees/{id}
     * 编辑员工（白名单字段）
     *
     * 允许: name, gender, email, birthday, residenceAddress,
     *       emergencyContact, emergencyPhone, workLocation
     * 拒绝: departmentId, positionId, grade, managerId → 20003
     *       mobile → 走 MOBILE_CHANGE 审批
     *       已离职 → 30003
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                                @Valid @RequestBody EmployeeUpdateDTO dto) {
        // 校验非白名单字段（通过反射检查传入的字段名）
        // 具体校验由 Service 层实现
        employeeService.update(id, dto);
        return Result.success();
    }

    /**
     * GET /api/v1/employees/{id}/salary
     * 薪资档案
     * SYS_ADMIN 返回 20002（由权限拦截器处理）
     */
    @GetMapping("/{id}/salary")
    public Result<?> getSalary(@PathVariable Long id) {
        return Result.success(null);
    }

    /**
     * PUT /api/v1/employees/{id}/salary
     */
    @PutMapping("/{id}/salary")
    public Result<Void> updateSalary(@PathVariable Long id,
                                      @RequestBody Object dto) {
        return Result.success();
    }

    /**
     * GET /api/v1/employees/{id}/sensitive/{field}
     * 敏感字段（二次验证）
     * 无权限 → 20003
     */
    @GetMapping("/{id}/sensitive/{field}")
    public Result<?> getSensitiveField(@PathVariable Long id,
                                        @PathVariable String field) {
        return Result.success(null);
    }

    /**
     * GET /api/v1/employees/{id}/transfer-history
     * 调岗历史
     */
    @GetMapping("/{id}/transfer-history")
    public Result<?> getTransferHistory(@PathVariable Long id) {
        return Result.success(employeeService.getTransferHistory(id));
    }

    /**
     * GET /api/v1/employees/mobile-change-applications
     * HR 手机号变更待办
     */
    @GetMapping("/mobile-change-applications")
    public Result<?> listMobileChangeApps() {
        return Result.success(employeeService.listMobileChangeApps());
    }
}
