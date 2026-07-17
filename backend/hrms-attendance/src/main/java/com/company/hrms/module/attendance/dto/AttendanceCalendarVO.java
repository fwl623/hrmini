package com.company.hrms.module.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 考勤日历 — 月度响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceCalendarVO {
    private int year;
    private int month;
    private List<AttendanceCalendarDay> days;
}
