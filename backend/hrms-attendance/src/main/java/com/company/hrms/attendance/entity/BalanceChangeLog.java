package com.company.hrms.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 假期余额变动日志
 * DDL: balance_change_log (V14)
 */
@Data
@TableName("balance_change_log")
public class BalanceChangeLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("employee_id")
    private Long employeeId;

    @TableField("leave_type")
    private String leaveType;

    /** 变动数量（正=消耗预扣，负=归还） */
    @TableField("change_amount")
    private BigDecimal changeAmount;

    /** SUBMIT / CONFIRM / REFUND / OVERTIME / EXPIRE */
    @TableField("source_type")
    private String sourceType;

    /** 关联源ID（leave_application.id / overtime_ledger.id） */
    @TableField("source_id")
    private Long sourceId;

    @TableField("balance_before")
    private BigDecimal balanceBefore;

    @TableField("balance_after")
    private BigDecimal balanceAfter;

    /** PENDING / CONFIRMED / REFUNDED */
    @TableField("status")
    private String status;

    @TableField("remark")
    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
