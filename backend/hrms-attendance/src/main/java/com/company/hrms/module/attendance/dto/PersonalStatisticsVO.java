package com.company.hrms.module.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 个人统计 VO（8 项指标）
 */
@Data
@AllArgsConstructor
public class PersonalStatisticsVO {
    private Long employeeId;
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
