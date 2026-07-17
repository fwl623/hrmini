package com.company.hrms.module.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 今日打卡状态 VO
 * <p>统计当前员工当日的打卡情况：已打卡次数、应打卡次数、迟到/早退/缺卡数量。</p>
 */
@Data
@AllArgsConstructor
public class TodayPunchVO {
    private long clockedCount;    // 已打卡次数
    private long totalCount;      // 应打卡次数（2=上午+下午）
    private long lateCount;       // 迟到次数
    private long earlyLeaveCount; // 早退次数
    private long absentCount;     // 旷工次数
}
