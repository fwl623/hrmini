package com.company.hrms.module.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.company.hrms.common.entity.BaseEntity;
import com.company.hrms.module.employee.enums.ContractType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同与薪资配置
 * 对应表: employee_contract
 */
@TableName("employee_contract")
public class EmployeeContractEntity extends BaseEntity {

    /** 员工ID */
    private Long employeeId;

    /** 合同类型 FIXED / UNFIXED / LABOR */
    private ContractType contractType;

    /** 合同到期日（固定期限必填） */
    private LocalDate contractExpireDate;

    /** 试用期薪资比例 0.80-1.00 */
    private BigDecimal probationSalaryRatio;

    /** 账套ID（关联 payroll_scheme.id） */
    private Long schemeId;

    /** 基本工资 */
    private BigDecimal baseSalary;

    // ===== Getters & Setters =====

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public ContractType getContractType() { return contractType; }
    public void setContractType(ContractType contractType) { this.contractType = contractType; }

    public LocalDate getContractExpireDate() { return contractExpireDate; }
    public void setContractExpireDate(LocalDate contractExpireDate) { this.contractExpireDate = contractExpireDate; }

    public BigDecimal getProbationSalaryRatio() { return probationSalaryRatio; }
    public void setProbationSalaryRatio(BigDecimal probationSalaryRatio) { this.probationSalaryRatio = probationSalaryRatio; }

    public Long getSchemeId() { return schemeId; }
    public void setSchemeId(Long schemeId) { this.schemeId = schemeId; }

    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }
}
