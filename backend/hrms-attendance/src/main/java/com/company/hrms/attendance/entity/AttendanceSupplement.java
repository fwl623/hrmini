package com.company.hrms.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 补卡申请表
 * <p>员工因漏打卡等异常情况申请补正考勤记录。</p>
 * <p>补卡需要经过审批流程，审批通过后系统自动修正对应的打卡记录。</p>
 * <p>每月补卡次数上限为 2 次，月锁定后不可补卡。</p>
 * DDL: attendance_supplement (#45)
 */
@Data
@TableName("attendance_supplement")
public class AttendanceSupplement {

    /**
     * 主键 ID，自增
     * 唯一标识一条补卡申请记录
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 员工 ID，关联 employee 表主键
     * 用于标识提交补卡申请的员工
     */
    @TableField("employee_id")
    private Long employeeId;

    /**
     * 补卡日期
     * 格式：yyyy-MM-dd，表示需要补卡的考勤日期
     * 当月及前月可补卡
     */
    @TableField("makeup_date")
    private String makeupDate;

    /**
     * 补卡类型
     * <ul>
     *   <li><b>IN</b> — 补上班卡</li>
     *   <li><b>OUT</b> — 补下班卡</li>
     * </ul>
     */
    @TableField("punch_type")
    private String punchType;

    /**
     * 补卡时间
     * 格式：yyyy-MM-dd HH:mm:ss，员工声称的打卡时间
     */
    @TableField("makeup_time")
    private LocalDateTime makeupTime;

    /**
     * 补卡原因
     * 员工填写的漏打卡原因说明，不超过 256 字符
     */
    @TableField("reason")
    private String reason;

    /**
     * 审批状态
     * <ul>
     *   <li><b>PENDING</b> — 待审批</li>
     *   <li><b>APPROVED</b> — 已通过，系统将自动修正考勤记录</li>
     *   <li><b>REJECTED</b> — 已驳回</li>
     * </ul>
     */
    @TableField("status")
    private String status;

    /**
     * 审批实例 ID，关联 approval_instance 表主键
     * 用于关联该补卡申请对应的审批流程记录
     */
    @TableField("instance_id")
    private Long instanceId;

    /**
     * 创建时间
     * 格式：yyyy-MM-dd HH:mm:ss，记录该补卡申请的提交时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
