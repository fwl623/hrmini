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
 * 日考勤汇总
 * <p>记录每位员工每天的综合考勤结果，由系统每日凌晨 02:00 批处理生成。</p>
 * <p>根据当日打卡流水（attendance_record）自动计算状态，</p>
 * <p>并结合请假、加班等数据完成聚合。为月考勤汇总（attendance_monthly_summary）提供基础数据。</p>
 * DDL: attendance_daily_summary (#44)
 */
@Data
@TableName("attendance_daily_summary")
public class AttendanceDailySummary {

    /**
     * 主键 ID，自增
     * 唯一标识一条日考勤汇总记录
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 员工 ID，关联 employee 表主键
     * 用于标识该日汇总所属的员工
     */
    @TableField("employee_id")
    private Long employeeId;

    /**
     * 汇总日期
     * 格式：yyyy-MM-dd，标识该汇总记录对应的考勤日期
     */
    @TableField("summary_date")
    private LocalDate summaryDate;

    /**
     * 日考勤状态（系统自动判定）
     * <ul>
     *   <li><b>NORMAL</b> — 正常出勤（有上下班打卡且无异常）</li>
     *   <li><b>LATE</b> — 迟到（上班打卡晚于规定时间+宽限阈值）</li>
     *   <li><b>EARLY_LEAVE</b> — 早退（下班打卡早于规定时间-宽限阈值）</li>
     *   <li><b>ABSENT_HALF</b> — 半日缺勤（仅有一次打卡记录）</li>
     *   <li><b>ABSENT</b> — 全天缺勤（无打卡记录）</li>
     *   <li><b>MISSING_IN</b> — 缺上班卡（有下班卡无上班卡）</li>
     *   <li><b>MISSING_OUT</b> — 缺下班卡（有上班卡无下班卡）</li>
     *   <li><b>LEAVE</b> — 请假（当日存在请假记录）</li>
     * </ul>
     */
    @TableField("day_status")
    private String dayStatus;

    /**
     * 上班打卡时间
     * 格式：yyyy-MM-dd HH:mm:ss，员工当日最早的上班打卡记录时间
     */
    @TableField("clock_in_time")
    private LocalDateTime clockInTime;

    /**
     * 下班打卡时间
     * 格式：yyyy-MM-dd HH:mm:ss，员工当日最晚的下班打卡记录时间
     */
    @TableField("clock_out_time")
    private LocalDateTime clockOutTime;

    /**
     * 当日请假天数
     * 单位：天，精确到 0.5（半天）
     * 0 表示当日无请假；0.5 表示请假半天；1.0 表示全天请假
     */
    @TableField("leave_days")
    private BigDecimal leaveDays;

    /**
     * 当日加班小时数
     * 单位：小时，保留两位小数
     * 0 表示当日无加班；2.5 表示加班 2 小时 30 分钟
     */
    @TableField("overtime_hours")
    private BigDecimal overtimeHours;
}
