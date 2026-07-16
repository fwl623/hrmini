package com.company.hrms.employee.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 调薪历史表
 * DDL: #30 employee_salary_history
 */
@Data
public class EmployeeSalaryHistory {
    private Long id;
    private Long employeeId;
    private String fieldName;
    private BigDecimal oldValue;
    private BigDecimal newValue;
    private LocalDate effectiveDate;
    private String reason;
    private Long operatorId;
    private LocalDateTime createdAt;
}
