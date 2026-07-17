package com.company.hrms.employee.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 薪资档案更新请求参数
 * <p>
 * PUT /api/v1/employees/{id}/salary
 * HR/财务专员可更新员工薪资档案信息。
 * 更新时会自动记录调薪历史到 employee_salary_history 表。
 * SYS_ADMIN 角色被拦截，返回 403。
 * </p>
 */
@Data
public class SalaryProfileUpdateDTO {

    /** 薪资账套ID（关联 payroll_scheme.id） */
    private Long schemeId;

    /** 基本工资 */
    private BigDecimal baseSalary;

    /** 津贴基数 JSON */
    private String allowanceBaseJson;

    /** 社保基数 */
    private BigDecimal ssBase;

    /** 公积金基数 */
    private BigDecimal hfBase;

    /** 绩效基数 */
    private BigDecimal performanceBase;

    /** 试用期比例（0.80 ~ 1.00） */
    private BigDecimal probationRatio;
}
