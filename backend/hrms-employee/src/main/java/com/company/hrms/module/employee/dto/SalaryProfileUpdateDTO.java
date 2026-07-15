package com.company.hrms.module.employee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 薪资档案更新请求
 * PUT /api/v1/employees/{id}/salary
 */
@Data
@Schema(description = "薪资档案更新请求")
public class SalaryProfileUpdateDTO {

    @NotNull
    @Schema(description = "账套ID")
    private Long schemeId;

    @NotNull
    @Schema(description = "基本工资")
    private BigDecimal baseSalary;

    @Schema(description = "津贴基数 JSON")
    private String allowanceBaseJson;

    @NotNull
    @Schema(description = "社保基数")
    private BigDecimal ssBase;

    @NotNull
    @Schema(description = "公积金基数")
    private BigDecimal hfBase;

    @Schema(description = "绩效基数")
    private BigDecimal performanceBase;

    @Schema(description = "试用期比例 0.80-1.00")
    private BigDecimal probationRatio;
}
