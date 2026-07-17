package com.company.hrms.employee.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 调薪历史表实体
 * <p>
 * 对应表: employee_salary_history (DDL #30)
 * 记录员工每次薪资变更的详细历史（变更字段、前后值、操作人等）。
 * 每次薪资档案编辑自动写入，用于薪资审计追溯。
 * </p>
 */
@Data
public class EmployeeSalaryHistory {

    /** 主键ID */
    private Long id;

    /** 员工ID，关联 employee.id */
    private Long employeeId;

    /** 变更字段名（如 baseSalary、ssBase、probationRatio） */
    private String fieldName;

    /** 变更前值 */
    private BigDecimal oldValue;

    /** 变更后值 */
    private BigDecimal newValue;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 变更原因 */
    private String reason;

    /** 操作人 employee_id */
    private Long operatorId;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
