package com.company.hrms.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 假期余额表
 * DDL: leave_balance (#46)
 */
@Data
@TableName("leave_balance")
public class LeaveBalance {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("employee_id")
    private Long employeeId;

    @TableField("leave_type")
    private String leaveType;

    /** 年度/周期总配额 */
    @TableField("total_quota")
    private BigDecimal totalQuota;

    /** 已使用额度 */
    @TableField("used_quota")
    private BigDecimal usedQuota;

    /** 剩余可用额度 (decimal(10,3) 精确到 0.125) */
    @TableField("remaining_quota")
    private BigDecimal remainingQuota;

    /** 生效日期 */
    @TableField("effective_date")
    private LocalDate effectiveDate;

    /** 假期剩余余额（旧字段，与 remaining_quota 保持同步） */
    @TableField("balance")
    private BigDecimal balance;

    @TableField("year")
    private Integer year;

    /** 调休过期日期 */
    @TableField("expire_date")
    private LocalDate expireDate;

    /** 乐观锁版本号 */
    @Version
    @TableField("version")
    private Integer version;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
