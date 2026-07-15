package com.company.hrms.module.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 员工列表响应VO
 * GET /api/v1/employees 列表项
 */
@Data
@Schema(description = "员工列表项")
public class EmployeeListVO {

    @Schema(description = "员工ID")
    private Long employeeId;

    @Schema(description = "工号")
    private String empNo;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "部门名称")
    private String department;

    @Schema(description = "职位名称")
    private String position;

    @Schema(description = "职级")
    private String grade;

    @Schema(description = "在职状态")
    private String employmentStatus;

    @Schema(description = "入职日期")
    private LocalDate hireDate;
}
