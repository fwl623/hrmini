package com.company.hrms.module.attendance.dto;

import lombok.Data;

/**
 * 打卡记录 VO
 */
@Data
public class PunchRecordVO {
    private Long employeeId;
    private String employeeName;
    private String departmentName;
    private String punchDate;
    private String clockInTime;
    private String clockInStatus;
    private String clockOutTime;
    private String clockOutStatus;
    private String source;
    private String clientIp;
    private String gpsJson;
}
