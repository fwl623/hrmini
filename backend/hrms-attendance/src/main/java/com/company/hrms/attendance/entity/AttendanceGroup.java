package com.company.hrms.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 考勤组
 * DDL: attendance_group (#22)
 */
@Data
@TableName("attendance_group")
public class AttendanceGroup {

    /**
     * 考勤组主键 ID
     * 自增主键，由数据库自动生成
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 考勤组名称
     * 用于标识考勤组，如"研发部考勤组"、"销售部弹性考勤组"等，不可为空
     */
    @TableField("name")
    private String name;

    /**
     * 班次类型
     * 可选值：
     * <ul>
     *   <li><b>FIXED</b>    - 固定班次：所有成员按统一的上下班时间打卡</li>
     *   <li><b>FLEXIBLE</b> - 弹性班次：在弹性范围内自由打卡，满足时长即可</li>
     *   <li><b>SCHEDULE</b> - 排班制：按排班表确定每日班次</li>
     * </ul>
     */
    @TableField("shift_type")
    private String shiftType;

    /**
     * 上班时间
     * 格式：HH:mm（24 小时制）
     * 固定班次和排班制的标准上班打卡时间；弹性班次下参考此值
     */
    @TableField("work_start_time")
    private LocalTime workStartTime;

    /**
     * 下班时间
     * 格式：HH:mm（24 小时制）
     * 固定班次和排班制的标准下班打卡时间；弹性班次下参考此值
     */
    @TableField("work_end_time")
    private LocalTime workEndTime;

    /**
     * 午休开始时间
     * 格式：HH:mm（24 小时制）
     * 不计入工作时长的午休时段起点，可为 null（无午休）
     */
    @TableField("lunch_start_time")
    private LocalTime lunchStartTime;

    /**
     * 午休结束时间
     * 格式：HH:mm（24 小时制）
     * 不计入工作时长的午休时段终点，可为 null（无午休）
     */
    @TableField("lunch_end_time")
    private LocalTime lunchEndTime;

    /**
     * 弹性班次最早打卡时间
     * 格式：HH:mm（24 小时制）
     * 仅当 shift_type = FLEXIBLE 时生效，员工在此时间之前打卡无效
     */
    @TableField("flex_start_earliest")
    private LocalTime flexStartEarliest;

    /**
     * 弹性班次最晚打卡时间
     * 格式：HH:mm（24 小时制）
     * 仅当 shift_type = FLEXIBLE 时生效，员工超过此时间打卡视为迟到或无效
     */
    @TableField("flex_start_latest")
    private LocalTime flexStartLatest;

    /**
     * 迟到判定阈值
     * 单位：分钟（min）
     * 员工打卡时间晚于上班时间超过此值，记为"迟到"；
     * 若为 0 表示不允许迟到，若为 null 表示不启用迟到判定
     */
    @TableField("late_threshold_minutes")
    private Integer lateThresholdMinutes;

    /**
     * 早退判定阈值
     * 单位：分钟（min）
     * 员工打卡时间早于下班时间超过此值，记为"早退"；
     * 若为 0 表示不允许早退，若为 null 表示不启用早退判定
     */
    @TableField("early_leave_threshold_minutes")
    private Integer earlyLeaveThresholdMinutes;

    /**
     * 允许打卡的 IP 白名单（JSON 数组）
     * 格式示例：["192.168.1.0/24","10.0.0.1"]
     * 仅在指定网络环境下允许打卡，为空表示不限制 IP
     */
    @TableField("ip_whitelist_json")
    private String ipWhitelistJson;

    /**
     * 允许打卡的 GPS 范围（JSON 对象）
     * 格式示例：{"lat": 39.9042, "lng": 116.4074, "radiusM": 500}
     * <ul>
     *   <li><b>lat</b>     - 纬度 (WGS-84)</li>
     *   <li><b>lng</b>     - 经度 (WGS-84)</li>
     *   <li><b>radiusM</b> - 半径，单位：米（m）</li>
     * </ul>
     * 为空表示不限制打卡地理位置
     */
    @TableField("gps_range_json")
    private String gpsRangeJson;

    /**
     * 逻辑删除标记
     * 可选值：
     * <ul>
     *   <li><b>0</b> - 正常（未删除）</li>
     *   <li><b>1</b> - 已删除（逻辑删除，数据不可见）</li>
     * </ul>
     */
    @TableField("deleted")
    private Integer deleted;

    /**
     * 创建时间
     * 格式：yyyy-MM-dd HH:mm:ss
     * 记录考勤组的创建时间，插入时由数据库自动填充
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     * 格式：yyyy-MM-dd HH:mm:ss
     * 记录考勤组的最后修改时间，更新时由数据库自动刷新
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
