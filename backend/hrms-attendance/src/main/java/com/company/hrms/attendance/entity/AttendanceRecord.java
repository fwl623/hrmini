package com.company.hrms.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 打卡流水表
 *
 * 记录每位员工每次打卡的原始数据，是考勤系统的基础数据源。
 * 每日凌晨 02:00 由此表聚合生成 attendance_daily_summary。
 *
 * 打卡类型（punch_type）：IN=上班签到, OUT=下班签退
 * 打卡状态（punch_status）：由系统根据考勤组时间和规则自动判定
 * 打卡来源（source）：WEB/APP/MAKEUP(补卡)/CLOCK(打卡机)/WECHAT
 *
 * GPS 信息（gps_json）存储 JSON 格式的地理位置数据，
 * 用于考勤范围校验（Haversine 距离公式）。
 */
@Data
@TableName("attendance_record")
public class AttendanceRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 员工 ID，关联 employee 表主键 */
    @TableField("employee_id")
    private Long employeeId;

    /**
     * 考勤日（打卡日期）
     * 格式 yyyy-MM-dd，与 punch_time 可能跨天（如跨夜班）
     */
    @TableField("punch_date")
    private LocalDate punchDate;

    /**
     * 打卡时间
     * 格式 yyyy-MM-dd HH:mm:ss，精确到秒
     */
    @TableField("punch_time")
    private LocalDateTime punchTime;

    /**
     * 打卡类型
     * IN - 上班签到（上班卡）
     * OUT - 下班签退（下班卡）
     */
    @TableField("punch_type")
    private String punchType;

    /**
     * 打卡状态（系统自动判定）
     * NORMAL - 正常打卡
     * LATE - 迟到
     * EARLY_LEAVE - 早退
     * ABSENT_HALF - 半日缺勤（仅有一次打卡记录）
     * ABSENT - 全天缺勤（无打卡记录）
     * OVERTIME - 加班
     */
    @TableField("punch_status")
    private String punchStatus;

    /**
     * 打卡来源
     * WEB/APP/MAKEUP(补卡)/CLOCK(打卡机)/WECHAT(企业微信)
     */
    @TableField("source")
    private String source;

    /**
     * GPS 地理位置（JSON）
     * {"lat": 39.9042, "lng": 116.4074, "accuracy": 20.0}
     */
    @TableField("gps_json")
    private String gpsJson;

    /** 记录创建时间，由数据库自动填充 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
