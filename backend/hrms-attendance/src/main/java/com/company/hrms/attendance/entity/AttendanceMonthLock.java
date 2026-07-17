package com.company.hrms.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 月考勤锁定
 * DDL: attendance_month_lock (#11)
 */
@Data
@TableName("attendance_month_lock")
public class AttendanceMonthLock {

    /**
     * 主键 ID
     * 自增主键，无业务含义
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 账期（考勤所属月份）
     * 格式：YYYY-MM，例如 2025-07 表示 2025 年 7 月
     * 用于标识该锁定记录对应的考勤月份
     */
    @TableField("`year_month`")
    private String yearMonth;

    /**
     * 锁定状态
     * <ul>
     *   <li>10 — OPEN（未锁定，考勤数据可修改）</li>
     *   <li>20 — LOCKED（已锁定，考勤数据不可修改，通常表示月结完成）</li>
     * </ul>
     */
    @TableField("status")
    private Integer status;

    /**
     * 锁定时间
     * 格式：yyyy-MM-dd HH:mm:ss（例如 2025-07-15 18:30:00）
     * 记录该月份考勤被锁定时的精确时间戳
     */
    @TableField("locked_at")
    private LocalDateTime lockedAt;

    /**
     * 锁定人（员工 ID）
     * 关联 employee 表主键，标识执行锁定操作的用户
     */
    @TableField("locked_by")
    private Long lockedBy;
}
