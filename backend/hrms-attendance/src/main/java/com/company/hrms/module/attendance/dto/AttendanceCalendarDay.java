package com.company.hrms.module.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 考勤日历 — 单日数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceCalendarDay {
    /** 日期 yyyy-MM-dd */
    private String date;
    /** 考勤状态: NORMAL/LATE/EARLY_LEAVE/ABSENT/ABSENT_HALF/MISSING_IN/MISSING_OUT/LEAVE */
    private String dayStatus;
    /** 上班打卡时间 HH:mm */
    private String clockInTime;
    /** 下班打卡时间 HH:mm */
    private String clockOutTime;
}
