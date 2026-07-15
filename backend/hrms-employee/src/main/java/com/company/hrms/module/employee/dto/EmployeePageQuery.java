package com.company.hrms.module.employee.dto;

import com.company.hrms.common.dto.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 员工花名册分页搜索参数
 * GET /api/v1/employees
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "员工分页搜索参数")
public class EmployeePageQuery extends PageParam {

    @Schema(description = "关键词模糊搜索：姓名/工号/手机号")
    private String keyword;

    @Schema(description = "部门ID列表，逗号分隔")
    private String departmentIds;

    @Schema(description = "职位ID列表，逗号分隔")
    private String positionIds;

    @Schema(description = "在职状态筛选，逗号分隔（如 probation,regular）")
    private String employmentStatus;

    @Schema(description = "职级筛选，逗号分隔（如 P5,P6,P7）")
    private String gradeLevels;

    @Schema(description = "入职日期范围-起始 yyyy-MM-dd")
    private LocalDate hireDateFrom;

    @Schema(description = "入职日期范围-结束 yyyy-MM-dd")
    private LocalDate hireDateTo;
}
