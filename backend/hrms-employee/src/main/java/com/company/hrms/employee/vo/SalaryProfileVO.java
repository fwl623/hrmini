package com.company.hrms.employee.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 薪资档案响应
 * <p>
 * 用于 GET /api/v1/employees/{id}/salary 接口。
 * SYS_ADMIN 角色访问返回 403。
 * 其他角色按字段权限裁剪。
 * </p>
 */
@Data
public class SalaryProfileVO {

    /** 薪资档案ID */
    private Long id;

    /** 员工ID */
    private Long employeeId;

    /** 薪资账套ID（关联 payroll_scheme.id） */
    private Long schemeId;

    /** 账套名称（回填展示用） */
    private String schemeName;

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
