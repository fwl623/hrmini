package com.company.hrms.module.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 工资条列表摘要VO
 * GET /api/v1/profile/payslips
 */
@Data
@Schema(description = "工资条列表摘要")
public class PayslipListVO {

    @Schema(description = "账期 yyyy-MM")
    private String period;

    @Schema(description = "实发金额")
    private BigDecimal netSalary;

    @Schema(description = "发放状态")
    private String status;
}
