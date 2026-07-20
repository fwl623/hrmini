package com.company.hrms.module.attendance.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 加班申请 VO
 */
@Data
public class OvertimeApplicationVO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String department;
    private String overtimeDate;
    private String startTime;
    private String endTime;
    private BigDecimal hours;
    private String status;
}
