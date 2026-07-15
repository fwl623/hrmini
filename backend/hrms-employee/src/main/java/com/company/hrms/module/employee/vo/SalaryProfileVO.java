package com.company.hrms.module.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 薪资档案响应VO
 * GET /api/v1/employees/{id}/salary
 */
@Data
@Schema(description = "薪资档案")
public class SalaryProfileVO {

    @Schema(description = "薪资档案ID")
    private Long id;

    @Schema(description = "员工ID")
    private Long employeeId;

    @Schema(description = "账套ID")
    private Long schemeId;

    @Schema(description = "账套名称")
    private String schemeName;

    @Schema(description = "基本工资")
    private BigDecimal baseSalary;

    @Schema(description = "津贴基数 JSON")
    private String allowanceBaseJson;

    @Schema(description = "社保基数")
    private BigDecimal ssBase;

    @Schema(description = "公积金基数")
    private BigDecimal hfBase;

    @Schema(description = "绩效基数")
    private BigDecimal performanceBase;

    @Schema(description = "试用期比例")
    private BigDecimal probationRatio;
}
