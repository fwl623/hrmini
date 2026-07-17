package com.company.hrms.module.attendance.dto;

import lombok.Data;

/**
 * 请假申请 DTO
 */
@Data
public class LeaveApplicationDTO {
    private String leaveType;       // ANNUAL/SICK/PERSONAL/MARRIAGE/MATERNITY/BEREAVEMENT/COMP_OFF
    private String startTime;       // ISO datetime
    private String endTime;         // ISO datetime
    private Double days;            // 支持 0.5
    private String reason;
    private Long handoverEmployeeId;
    private String attachment;
}
