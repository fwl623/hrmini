package com.company.hrms.employee.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工合同表
 * DDL: #25 employee_contract
 */
@Data
public class EmployeeContract {
    private Long id;
    private Long employeeId;
    private String contractType;     // FIXED/UNFIXED/LABOR
    private LocalDate contractExpireDate;
    private BigDecimal probationSalaryRatio;
    private Long schemeId;
    private BigDecimal baseSalary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
