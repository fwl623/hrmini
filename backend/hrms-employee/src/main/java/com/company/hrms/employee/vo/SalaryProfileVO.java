package com.company.hrms.employee.vo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 薪资档案响应
 * GET /api/v1/employees/{id}/salary
 */
@Data
public class SalaryProfileVO {
    private Long id;
    private Long employeeId;
    private Long schemeId;
    private String schemeName;
    private BigDecimal baseSalary;
    private String allowanceBaseJson;
    private BigDecimal ssBase;
    private BigDecimal hfBase;
    private BigDecimal performanceBase;
    private BigDecimal probationRatio;
}
