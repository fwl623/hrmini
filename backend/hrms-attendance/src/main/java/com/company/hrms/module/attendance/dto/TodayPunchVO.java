package com.company.hrms.module.attendance.dto;

import lombok.Data;

import java.util.List;

/**
 * 今日打卡状态 VO
 * <p>统计当前员工当日的打卡情况：已打卡次数、应打卡次数、迟到/早退/缺卡数量。</p>
 */
@Data
public class TodayPunchVO {
    private long clockedCount;      // 已打卡次数
    private long totalCount;        // 应打卡次数（2=上午+下午）
    private long lateCount;         // 迟到次数
    private long earlyLeaveCount;   // 早退次数
    private long absentCount;       // 旷工次数

    /** 今日打卡记录明细（type/time/status） */
    private List<PunchRecordItem> records;

    public TodayPunchVO() {}

    public TodayPunchVO(long clockedCount, long totalCount, long lateCount, long earlyLeaveCount, long absentCount) {
        this.clockedCount = clockedCount;
        this.totalCount = totalCount;
        this.lateCount = lateCount;
        this.earlyLeaveCount = earlyLeaveCount;
        this.absentCount = absentCount;
    }

    @Data
    public static class PunchRecordItem {
        private String type;     // IN / OUT
        private String time;     // HH:mm
        private String status;   // NORMAL / LATE / ...
    }
}
