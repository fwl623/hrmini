package com.company.hrms.module.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.company.hrms.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 员工薪资档案
 * 对应表: employee_salary_profile
 */
@TableName("employee_salary_profile")
public class EmployeeSalaryProfileEntity extends BaseEntity {

    /** 员工ID */
    private Long employeeId;

    /** 账套ID（关联 payroll_scheme.id） */
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

    /** 试用期比例 0.80–1.00 */
    private BigDecimal probationRatio;

    /** 生效日期 */
    private LocalDate effectiveDate;

    // ===== Getters & Setters =====

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public Long getSchemeId() { return schemeId; }
    public void setSchemeId(Long schemeId) { this.schemeId = schemeId; }

    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }

    public String getAllowanceBaseJson() { return allowanceBaseJson; }
    public void setAllowanceBaseJson(String allowanceBaseJson) { this.allowanceBaseJson = allowanceBaseJson; }

    public BigDecimal getSsBase() { return ssBase; }
    public void setSsBase(BigDecimal ssBase) { this.ssBase = ssBase; }

    public BigDecimal getHfBase() { return hfBase; }
    public void setHfBase(BigDecimal hfBase) { this.hfBase = hfBase; }

    public BigDecimal getPerformanceBase() { return performanceBase; }
    public void setPerformanceBase(BigDecimal performanceBase) { this.performanceBase = performanceBase; }

    public BigDecimal getProbationRatio() { return probationRatio; }
    public void setProbationRatio(BigDecimal probationRatio) { this.probationRatio = probationRatio; }

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
}
