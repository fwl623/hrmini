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
 * <p>薪资核算批次的核心表，记录每个月的薪资核算任务。</p>
 * <p>批次状态机：DRAFT → CALCULATING → PENDING_CONFIRM → APPROVING → APPROVED → DISTRIBUTED</p>
 * <p>任何审批节点可驳回至 PENDING_CONFIRM，修改后重新提交。</p>
 * DDL: payroll_batch (#51)
 */
@Data
@TableName("payroll_batch")
public class PayrollBatch {

    /**
     * 主键 ID，自增
     * 唯一标识一个核算批次
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 核算账期
     * 格式：YYYY-MM，例如 2026-07 表示 2026 年 7 月
     * 一个账期仅允许一个批次，由唯一约束保证
     */
    @TableField("period")
    private String period;

    /**
     * 批次状态（状态机流转）
     * <ul>
     *   <li><b>DRAFT</b> — 草稿（初始状态，可修改）</li>
     *   <li><b>CALCULATING</b> — 计算中（MQ 异步核算）</li>
     *   <li><b>PENDING_CONFIRM</b> — 待确认（计算完成，HR 预览调整）</li>
     *   <li><b>APPROVING</b> — 审批中（已提交审批）</li>
     *   <li><b>APPROVED</b> — 已通过（审批通过）</li>
     *   <li><b>DISTRIBUTED</b> — 已发放（发放确认完成）</li>
     *   <li><b>REJECTED</b> — 已驳回（审批驳回，可修改后重新提交）</li>
     * </ul>
     */
    @TableField("status")
    private String status;

    /**
     * 参与核算总人数
     * 该批次需要核算的员工总数
     */
    @TableField("total_count")
    private Integer totalCount;

    /**
     * 核算成功人数
     * 成功完成薪资计算的员工数量
     */
    @TableField("success_count")
    private Integer successCount;

    /**
     * 应发工资合计
     * 单位：元，保留两位小数
     * 所有员工的应发工资（税前）总额
     */
    @TableField("gross_total")
    private BigDecimal grossTotal;

    /**
     * 实发工资合计
     * 单位：元，保留两位小数
     * 所有员工的实发工资（税后）总额
     */
    @TableField("net_total")
    private BigDecimal netTotal;

    /**
     * 异常员工数量
     * 核算过程中被标记为异常的员工作数
     */
    @TableField("anomaly_count")
    private Integer anomalyCount;

    /**
     * 审批实例 ID，关联 approval_instance 表主键
     * 用于关联该批次提交后的审批流程
     */
    @TableField("instance_id")
    private Long instanceId;

    /**
     * 考勤月是否已锁定
     * <ul>
     *   <li>0 — 未锁定（考勤数据可能还在变化）</li>
     *   <li>1 — 已锁定（考勤数据已冻结，可供核算引用）</li>
     * </ul>
     */
    @TableField("attendance_locked")
    private Integer attendanceLocked;

    /**
     * 创建人（员工 ID），关联 employee 表主键
     * 记录创建该批次的 HR 操作人员
     */
    @TableField("created_by")
    private Long createdBy;

    /**
     * 创建时间
     * 格式：yyyy-MM-dd HH:mm:ss，记录批次的创建时刻
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     * 格式：yyyy-MM-dd HH:mm:ss，记录批次的最后修改时刻
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
