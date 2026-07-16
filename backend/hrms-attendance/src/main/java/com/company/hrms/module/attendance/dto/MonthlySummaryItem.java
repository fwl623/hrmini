package com.company.hrms.module.attendance.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 月汇总明细项
 */
@Data
public class MonthlySummaryItem {
    private Long employeeId;
    private String employeeName;
    private String period;
    private int shouldAttendDays;
    private BigDecimal actualAttendDays;
    private int lateCount;
    private int earlyLeaveCount;
    private BigDecimal absentDays;
    private BigDecimal leaveDays;
    private BigDecimal overtimeHours;
}
