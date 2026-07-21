package com.company.hrms.module.attendance.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 个人统计 VO（8 项指标）
 */
@Data
public class PersonalStatisticsVO {
    private Long employeeId;
    private String employeeName;
    private String departmentName;
    private String period;
    private int shouldAttendDays;
    private BigDecimal actualAttendDays;
    private int lateCount;
    private int earlyLeaveCount;
    private BigDecimal absentDays;
    private BigDecimal leaveDays;
    private BigDecimal overtimeHours;
    private BigDecimal annualBalance;
}
