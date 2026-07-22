package com.company.hrms.payroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 月度核算批次表
 *
 * 薪资核算批次的核心表，记录每个月的薪资核算任务。
 * 一个账期只允许一个批次（由 uk_period 唯一约束保证）。
 *
 * 批次状态机流转：
 *   DRAFT → CALCULATING → PENDING_CONFIRM → APPROVING → APPROVED → DISTRIBUTED
 *                           ↑                                  │
 *                           └────────── REJECTED ←─────────────┘
 *
 * - DRAFT：初始状态，HR 创建后可修改
 * - CALCULATING：MQ 异步核算中
 * - PENDING_CONFIRM：核算完成，HR 可预览并手工调整
 * - APPROVING：已提交审批
 * - APPROVED：审批通过，可发放
 * - DISTRIBUTED：已发放确认（最终状态）
 * - REJECTED：审批驳回，可修改后重新提交
 */
@Data
@TableName("payroll_batch")
public class PayrollBatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 核算账期，格式 YYYY-MM，唯一约束 */
    @TableField("period")
    private String period;

    /** 批次状态（如上状态机所示） */
    @TableField("status")
    private String status;

    /** 参与核算总人数 */
    @TableField("total_count")
    private Integer totalCount;

    /** 核算成功人数 */
    @TableField("success_count")
    private Integer successCount;

    /** 应发工资合计（税前） */
    @TableField("gross_total")
    private BigDecimal grossTotal;

    /** 实发工资合计（税后） */
    @TableField("net_total")
    private BigDecimal netTotal;

    /** 异常员工数量（核算中被标记为异常的员工作数） */
    @TableField("anomaly_count")
    private Integer anomalyCount;

    /** 审批实例 ID，关联 approval_instance 表 */
    @TableField("instance_id")
    private Long instanceId;

    /** 考勤月是否已锁定（0=未锁定, 1=已锁定） */
    @TableField("attendance_locked")
    private Integer attendanceLocked;

    /** 创建人（HR 操作人员） */
    @TableField("created_by")
    private Long createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
