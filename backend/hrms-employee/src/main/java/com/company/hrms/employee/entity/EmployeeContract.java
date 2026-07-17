package com.company.hrms.employee.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工合同表实体
 * <p>
 * 对应表: employee_contract (DDL #25)
 * 存储员工的合同信息及基本工资配置。
 * 与 employee 表一对一关系（uk_employee），入职时创建。
 * </p>
 */
@Data
public class EmployeeContract {

    /** 主键ID */
    private Long id;

    /** 员工ID，关联 employee.id */
    private Long employeeId;

    /** 合同类型：FIXED=固定期限 UNFIXED=无固定期限 LABOR=劳务合同 */
    private String contractType;

    /** 合同到期日（固定期限合同必填） */
    private LocalDate contractExpireDate;

    /** 试用期待遇比例，范围 0.80~1.00 */
    private BigDecimal probationSalaryRatio;

    /** 薪资账套ID，关联 payroll_scheme.id */
    private Long schemeId;

    /** 基本工资 */
    private BigDecimal baseSalary;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
