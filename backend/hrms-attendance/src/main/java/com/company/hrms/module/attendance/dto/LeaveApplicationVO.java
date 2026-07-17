package com.company.hrms.module.attendance.dto;

import lombok.Data;

/**
 * 请假申请 VO
 */
@Data
public class LeaveApplicationVO {
    private Long id;
    private String employeeName;
    private String leaveType;
    private String startTime;
    private String endTime;
    private Double leaveDays;
    private String reason;
    private String status;
}
