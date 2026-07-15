package com.company.hrms.module.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.company.hrms.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 调薪历史
 * 对应表: employee_salary_history
 */
@TableName("employee_salary_history")
public class EmployeeSalaryHistoryEntity extends BaseEntity {

    /** 员工ID */
    private Long employeeId;

    /** 调薪字段名（如 baseSalary） */
    private String fieldName;

    /** 旧值 */
    private BigDecimal oldValue;

    /** 新值 */
    private BigDecimal newValue;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 调薪原因 */
    private String reason;

    /** 操作人ID */
    private Long operatorId;

    // ===== Getters & Setters =====

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public BigDecimal getOldValue() { return oldValue; }
    public void setOldValue(BigDecimal oldValue) { this.oldValue = oldValue; }

    public BigDecimal getNewValue() { return newValue; }
    public void setNewValue(BigDecimal newValue) { this.newValue = newValue; }

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
}
