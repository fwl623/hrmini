package com.company.hrms.module.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 工资条趋势VO
 * GET /api/v1/profile/payslips/trend
 */
@Data
@Schema(description = "近6月工资趋势")
public class PayslipTrendVO {

    @Schema(description = "账期 yyyy-MM")
    private String period;

    @Schema(description = "实发金额")
    private BigDecimal netSalary;
}
