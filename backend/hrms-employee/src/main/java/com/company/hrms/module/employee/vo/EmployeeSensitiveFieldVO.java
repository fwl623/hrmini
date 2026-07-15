package com.company.hrms.module.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 敏感字段响应VO
 * GET /api/v1/employees/{id}/sensitive/{field}
 */
@Data
@Schema(description = "敏感字段响应")
public class EmployeeSensitiveFieldVO {

    @Schema(description = "员工ID")
    private Long employeeId;

    @Schema(description = "字段名")
    private String field;

    @Schema(description = "字段值（完整明文）")
    private String value;
}
