package com.company.hrms.employee.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工薪资档案表
 * DDL: #29 employee_salary_profile
 */
@Data
public class EmployeeSalaryProfile {
    private Long id;
    private Long employeeId;
    private Long schemeId;
    private BigDecimal baseSalary;
    private String allowanceBaseJson; // JSON
    private BigDecimal ssBase;
    private BigDecimal hfBase;
    private BigDecimal performanceBase;
    private BigDecimal probationRatio;
    private LocalDate effectiveDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
