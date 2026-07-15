package com.company.hrms.module.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 调岗历史VO
 * GET /api/v1/employees/{id}/transfer-history
 */
@Data
@Schema(description = "调岗历史")
public class EmployeeTransferHistoryVO {

    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "员工ID")
    private Long employeeId;

    @Schema(description = "原部门名称")
    private String fromDepartment;

    @Schema(description = "新部门名称")
    private String toDepartment;

    @Schema(description = "原职位名称")
    private String fromPosition;

    @Schema(description = "新职位名称")
    private String toPosition;

    @Schema(description = "调岗日期")
    private LocalDate transferDate;

    @Schema(description = "原因")
    private String reason;

    @Schema(description = "创建时间")
    private String createdAt;
}
