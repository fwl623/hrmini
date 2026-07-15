package com.company.hrms.module.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 工资条详情VO
 * GET /api/v1/profile/payslips/{period}
 */
@Data
@Schema(description = "工资条详情")
public class PayslipDetailVO {

    @Schema(description = "账期 yyyy-MM")
    private String period;

    @Schema(description = "员工姓名")
    private String employeeName;

    @Schema(description = "工号")
    private String empNo;

    @Schema(description = "部门")
    private String department;

    @Schema(description = "收入项")
    private List<PayItem> earnings;

    @Schema(description = "应发合计")
    private BigDecimal grossSalary;

    @Schema(description = "扣款项")
    private List<PayItem> deductions;

    @Schema(description = "扣款合计")
    private BigDecimal totalDeduction;

    @Schema(description = "实发工资")
    private BigDecimal netSalary;

    @Data
    @Schema(description = "工资项目")
    public static class PayItem {
        @Schema(description = "项目名称")
        private String name;

        @Schema(description = "金额")
        private BigDecimal amount;
    }
}
