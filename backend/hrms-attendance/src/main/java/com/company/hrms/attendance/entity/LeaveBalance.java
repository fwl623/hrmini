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
 *
 * 存储员工各类假期（年假 ANNUAL、调休 COMP_OFF）的可用余额。
 * 每条记录按员工+类型+年份唯一标识。
 *
 * 核心字段说明：
 *   - total_quota：年度总配额（年假按工龄计算）
 *   - used_quota：已使用额度
 *   - remaining_quota：剩余可用额度（精确到 0.125）
 *   - balance：旧余额字段（与 remaining_quota 保持同步）
 *
 * 乐观锁：
 *   @Version 注解在 version 字段上，用于防止并发请假时的余额超扣。
 *   提交请假时先扣减余额，若 version 不匹配则更新失败（返回 0 行），
 *   业务层捕获后抛异常要求重试。
 */
@Data
@TableName("leave_balance")
public class LeaveBalance {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 员工 ID，关联 employee 表 */
    @TableField("employee_id")
    private Long employeeId;

    /** 假期类型：ANNUAL（年假）/ COMP_OFF（调休） */
    @TableField("leave_type")
    private String leaveType;

    /** 年度总配额（天） */
    @TableField("total_quota")
    private BigDecimal totalQuota;

    /** 已使用额度（天） */
    @TableField("used_quota")
    private BigDecimal usedQuota;

    /** 剩余可用额度（天，精确到 0.125） */
    @TableField("remaining_quota")
    private BigDecimal remainingQuota;

    /** 生效日期 */
    @TableField("effective_date")
    private LocalDate effectiveDate;

    /** 假期剩余余额（旧字段，与 remaining_quota 同步） */
    @TableField("balance")
    private BigDecimal balance;

    /** 所属年份 */
    @TableField("year")
    private Integer year;

    /** 调休过期日期（调休有时效性） */
    @TableField("expire_date")
    private LocalDate expireDate;

    /**
     * 乐观锁版本号
     * MyBatis-Plus @Version 自动递增，更新时校验
     */
    @Version
    @TableField("version")
    private Integer version;

    /** 最后更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
