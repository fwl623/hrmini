package com.company.hrms.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日考勤汇总表
 *
 * 记录每位员工每天的综合考勤结果，由系统在打卡时实时更新
 * 以及每日凌晨 02:00 批处理生成。
 *
 * day_status 格式（v2.1 双槽位）：
 *   "am:code,pm:code"
 *   其中 code 含义：
 *     0 = 正常出勤
 *     1 = 迟到(AM) / 早退(PM)
 *     2 = 早退(PM专用)
 *     3 = 旷工（远超迟到/早退阈值）
 *     4 = 请假（当日存在已审批请假覆盖该槽位）
 *     5 = 缺卡（无对应类型打卡记录）
 *
 * 兼容旧格式：直接存储 NORMAL/LATE/EARLY_LEAVE/ABSENT_HALF/ABSENT/LEAVE
 *
 * 请假天数（leave_days）和加班时长（overtime_hours）独立存储，
 * 不受双槽位状态影响。
 */
@Data
@TableName("attendance_daily_summary")
public class AttendanceDailySummary {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 员工 ID，关联 employee 表 */
    @TableField("employee_id")
    private Long employeeId;

    /** 汇总日期，格式 yyyy-MM-dd */
    @TableField("summary_date")
    private LocalDate summaryDate;

    /**
     * 日考勤状态（v2.1 双槽位 "am:code,pm:code"）
     * 也兼容旧格式字符串
     */
    @TableField("day_status")
    private String dayStatus;

    /** 上班打卡时间（当天最早的 IN 记录） */
    @TableField("clock_in_time")
    private LocalDateTime clockInTime;

    /** 下班打卡时间（当天最晚的 OUT 记录） */
    @TableField("clock_out_time")
    private LocalDateTime clockOutTime;

    /** 当日请假天数，支持 0.5（半天） */
    @TableField("leave_days")
    private BigDecimal leaveDays;

    /** 当日加班小时数，保留 2 位小数 */
    @TableField("overtime_hours")
    private BigDecimal overtimeHours;
}
