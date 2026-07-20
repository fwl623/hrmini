package com.company.hrms.module.attendance.dto;

import lombok.Data;

/**
 * 请假申请 VO
 */
@Data
public class LeaveApplicationVO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String department;
    private String leaveType;
    private String startTime;
    private String endTime;
    private Double leaveDays;
    private String reason;
    private String status;
    /** 审批实例 ID，门户查看进度用 */
    private Long instanceId;
}
