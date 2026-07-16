package com.company.hrms.employee.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 薪资档案更新请求
 * PUT /api/v1/employees/{id}/salary
 */
@Data
public class SalaryProfileUpdateDTO {
    private Long schemeId;
    private BigDecimal baseSalary;
    private String allowanceBaseJson;
    private BigDecimal ssBase;
    private BigDecimal hfBase;
    private BigDecimal performanceBase;
    private BigDecimal probationRatio;
}
