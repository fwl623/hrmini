package com.company.hrms.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 月考勤汇总表
 *
 * 由 aggregateToMonthly() 从日汇总聚合生成，
 * 记录每位员工每月汇总级别的考勤数据。
 *
 * 这些数据被薪资核算模块（CalculateService）读取，
 * 用于计算迟到扣款（LATE_DEDUCT）和请假扣款（LEAVE_DEDUCT）。
 *
 * detail_json 字段存储当月每日考勤明细的冗余快照，
 * 用于快速展示和历史归档，避免逐日联表查询。
 */
@Data
@TableName("attendance_monthly_summary")
public class AttendanceMonthlySummary {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 员工 ID，关联 employee 表 */
    @TableField("employee_id")
    private Long employeeId;

    /** 账期，格式 yyyy-MM（如 2026-07） */
    @TableField("period")
    private String period;

    /** 应出勤天数（按工作日配置和节假日动态计算） */
    @TableField("should_attend_days")
    private Integer shouldAttendDays;

    /** 实际出勤天数（支持 0.5） */
    @TableField("actual_attend_days")
    private BigDecimal actualAttendDays;

    /** 迟到次数 */
    @TableField("late_count")
    private Integer lateCount;

    /** 早退次数 */
    @TableField("early_leave_count")
    private Integer earlyLeaveCount;

    /** 旷工天数（支持 0.5） */
    @TableField("absent_days")
    private BigDecimal absentDays;

    /** 请假天数（各类请假合计） */
    @TableField("leave_days")
    private BigDecimal leaveDays;

    /** 加班总时长（小时） */
    @TableField("overtime_hours")
    private BigDecimal overtimeHours;

    /** 年假余额（截至该账期剩余） */
    @TableField("annual_balance")
    private BigDecimal annualBalance;

    /**
     * 明细快照（JSON 数组）
     * [{"date":"2026-07-01","type":"NORMAL","clockIn":"09:00","clockOut":"18:00",...}]
     */
    @TableField("detail_json")
    private String detailJson;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
