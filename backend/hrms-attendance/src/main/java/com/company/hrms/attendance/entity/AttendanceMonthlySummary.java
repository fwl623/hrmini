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
 * 记录每位员工每月汇总级别的考勤数据，包括出勤、迟到、早退、旷工、请假、加班及年假余额等信息。
 * DDL: attendance_monthly_summary (#50)
 */
@Data
@TableName("attendance_monthly_summary")
public class AttendanceMonthlySummary {

    /**
     * 主键 ID
     * 自增主键，无业务含义
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 员工 ID
     * 关联 employee 表主键，用于标识该月考勤汇总所属的员工
     */
    @TableField("employee_id")
    private Long employeeId;

    /**
     * 账期
     * 格式：yyyy-MM，例如 2026-07 表示 2026 年 7 月的考勤汇总
     */
    @TableField("period")
    private String period;

    /**
     * 应出勤天数
     * 单位：天。根据该月考勤日历计算得出的理论应出勤工作日数，不含法定节假日和周末
     */
    @TableField("should_attend_days")
    private Integer shouldAttendDays;

    /**
     * 实际出勤天数
     * 单位：天。员工当月实际打卡出勤的总天数，包含半天出勤场景（如 0.5）
     */
    @TableField("actual_attend_days")
    private BigDecimal actualAttendDays;

    /**
     * 迟到次数
     * 单位：次。员工当月迟到的总次数，每次迟到计 1 次
     */
    @TableField("late_count")
    private Integer lateCount;

    /**
     * 早退次数
     * 单位：次。员工当月早退的总次数，每次早退计 1 次
     */
    @TableField("early_leave_count")
    private Integer earlyLeaveCount;

    /**
     * 旷工天数
     * 单位：天。员工当月未经批准的缺勤天数，包含全天旷工和按小时折算的旷工（如 0.5）
     */
    @TableField("absent_days")
    private BigDecimal absentDays;

    /**
     * 请假天数
     * 单位：天。员工当月各类请假（年假、事假、病假等）的总天数，支持半天（如 0.5）
     */
    @TableField("leave_days")
    private BigDecimal leaveDays;

    /**
     * 加班时长
     * 单位：小时。员工当月加班总时长，含工作日加班、休息日加班和法定节假日加班
     */
    @TableField("overtime_hours")
    private BigDecimal overtimeHours;

    /**
     * 年假余额
     * 单位：天。员工截至当前账期剩余的法定年假可休天数
     */
    @TableField("annual_balance")
    private BigDecimal annualBalance;

    /**
     * 明细快照
     * JSON 格式字符串，存储当月每日考勤明细的冗余快照，用于快速展示和历史归档。
     * 预期结构（示意）：
     * [
     *   {
     *     "date": "2026-07-01",
     *     "type": "NORMAL",       // 考勤类型：NORMAL（正常出勤）/ LATE（迟到）/ EARLY_LEAVE（早退）/ ABSENT（旷工）/ LEAVE（请假）/ OVERTIME（加班）
     *     "clockIn": "09:00:00",  // 上班打卡时间，可为 null
     *     "clockOut": "18:00:00", // 下班打卡时间，可为 null
     *     "leaveType": null,      // 请假类型：ANNUAL（年假）/ SICK（病假）/ PERSONAL（事假）等，非请假日为空
     *     "overtimeHours": 0      // 当日加班时长（小时）
     *   }
     * ]
     */
    @TableField("detail_json")
    private String detailJson;

    /**
     * 创建时间
     * 格式：yyyy-MM-dd'T'HH:mm:ss（ISO-8601 本地日期时间）
     * 记录该条汇总数据的生成时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
