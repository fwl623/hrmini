package com.company.hrms.employee.dto;

import com.company.hrms.common.web.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 员工分页搜索参数
 * GET /api/v1/employees
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EmployeePageQuery extends PageParam {
    private String keyword;
    private String departmentIds;
    private String positionIds;
    private String employmentStatus; // 逗号分隔：probation,regular
    private String gradeLevels;      // 逗号分隔：P5,P6
    private LocalDate hireDateFrom;
    private LocalDate hireDateTo;
}
