package com.company.hrms.employee.vo;

import lombok.Data;

/**
 * 调岗历史展示（含部门/职位名称）。
 */
@Data
public class TransferHistoryVO {
    private Long id;
    private Long employeeId;
    private Long transferAppId;
    private Long fromDepartmentId;
    private String fromDepartmentName;
    private Long toDepartmentId;
    private String toDepartmentName;
    private Long fromPositionId;
    private String fromPositionName;
    private Long toPositionId;
    private String toPositionName;
    private String transferDate;
    private String reason;
}
