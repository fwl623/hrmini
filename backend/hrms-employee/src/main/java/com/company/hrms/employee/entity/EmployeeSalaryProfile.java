package com.company.hrms.employee.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工薪资档案表实体
 * <p>
 * 对应表: employee_salary_profile (DDL #29)
 * 存储员工的计算薪资所需的各项参数。
 * 与 employee 表一对一关系，入职时自动创建占位记录。
 * </p>
 */
@Data
public class EmployeeSalaryProfile {

    /** 主键ID */
    private Long id;

    /** 员工ID，关联 employee.id */
    private Long employeeId;

    /** 薪资账套ID，关联 payroll_scheme.id */
    private Long schemeId;

    /** 基本工资 */
    private BigDecimal baseSalary;

    /** 各项津贴基数 JSON */
    private String allowanceBaseJson;

    /** 社保基数 */
    private BigDecimal ssBase;

    /** 公积金基数 */
    private BigDecimal hfBase;

    /** 绩效基数 */
    private BigDecimal performanceBase;

    /** 试用期待遇比例 */
    private BigDecimal probationRatio;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
