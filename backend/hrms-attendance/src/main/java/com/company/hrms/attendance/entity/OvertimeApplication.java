package com.company.hrms.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 加班申请表
 * DDL: overtime_application (#48)
 */
@Data
@TableName("overtime_application")
public class OvertimeApplication {

    /**
     * 主键 ID
     * 自增主键，唯一标识一条加班申请记录
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 员工ID，关联 employee 表主键
     * 用于标识提交该加班申请的员工
     */
    @TableField("employee_id")
    private Long employeeId;

    /**
     * 加班日期
     * 格式：yyyy-MM-dd（字符串类型）
     * 记录加班发生的具体日期，不含具体时间
     */
    @TableField("overtime_date")
    private String overtimeDate;

    /**
     * 加班开始时间
     * 格式：yyyy-MM-dd HH:mm:ss（LocalDateTime 类型）
     * 记录加班实际开始的具体时刻
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 加班结束时间
     * 格式：yyyy-MM-dd HH:mm:ss（LocalDateTime 类型）
     * 记录加班实际结束的具体时刻
     */
    @TableField("end_time")
    private LocalDateTime endTime;

    /**
     * 系统计算时长（小时）
     * 单位：小时，保留两位小数
     * 由系统根据 start_time 和 end_time 自动计算得出的加班总时长
     * 例如：2.50 表示 2 小时 30 分钟
     */
    @TableField("hours")
    private BigDecimal hours;

    /**
     * 加班原因
     * 员工填写的加班事由说明文本
     */
    @TableField("reason")
    private String reason;

    /**
     * 加班申请状态
     * 可选值：
     *   PENDING  - 待审批（员工提交后初始状态）
     *   APPROVED - 已通过（审批人同意）
     *   REJECTED - 已驳回（审批人拒绝）
     * 默认值：PENDING
     */
    @TableField("status")
    private String status;

    /**
     * 折算调休小时
     * 单位：小时，保留两位小数
     * 加班时长按企业规则折算为调休时长
     * 例如：加班 3 小时可能折算为 4.50 小时调休（根据倍率计算）
     */
    @TableField("comp_off_hours")
    private BigDecimal compOffHours;

    /**
     * 创建时间
     * 格式：yyyy-MM-dd HH:mm:ss（LocalDateTime 类型）
     * 记录该加班申请记录的创建时刻，由数据库自动填充
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
