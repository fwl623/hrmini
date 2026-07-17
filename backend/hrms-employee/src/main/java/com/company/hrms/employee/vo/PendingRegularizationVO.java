package com.company.hrms.employee.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 待转正员工简要信息。
 */
@Data
public class PendingRegularizationVO {
    private Long employeeId;
    private String empNo;
    private String name;
    private Long departmentId;
    private Long positionId;
    private LocalDate hireDate;
    private LocalDate probationEndDate;
    private String employmentStatus;
}
