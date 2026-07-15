package com.company.hrms.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 加班批准台账（供算薪）
 * DDL: overtime_ledger (#49)
 */
@Data
@TableName("overtime_ledger")
public class OvertimeLedger {

    /**
     * 主键ID
     * 自增主键，加班批准台账记录的唯一标识
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 员工ID，关联 employee 表主键
     * 用于标识该加班记录所属的员工，据此关联员工基本信息及薪资计算
     */
    @TableField("employee_id")
    private Long employeeId;

    /**
     * 关联的加班申请单ID，对应 overtime_application 表主键
     * 指向原始加班申请记录，用于追溯审批链与申请明细
     */
    @TableField("application_id")
    private Long applicationId;

    /**
     * 归属账期，格式：yyyy-MM（如 2026-07）
     * 标识该笔加班费计入哪个月份的薪资核算周期，用于薪资汇总与报表统计
     */
    @TableField("period")
    private String period;

    /**
     * 审批总加班时长（单位：小时）
     * 经审批确认的可用于薪资计算的加班总小时数，含小数点精度
     */
    @TableField("total_hours")
    private BigDecimal totalHours;

    /**
     * 加班倍率类型
     * 用于计算加班费时适用的工资倍数，枚举值说明：
     * <ul>
     *   <li>15 — 1.5 倍（工作日延时加班）</li>
     *   <li>20 — 2.0 倍（休息日加班）</li>
     *   <li>30 — 3.0 倍（法定节假日加班）</li>
     * </ul>
     */
    @TableField("rate_type")
    private Integer rateType;

    /**
     * 加班日期（格式：yyyy-MM-dd）
     * 该笔加班记录实际发生的日历日期，用于判定加班所属日期类型
     */
    @TableField("ledger_date")
    private LocalDate ledgerDate;

    /**
     * 创建时间（格式：yyyy-MM-dd HH:mm:ss）
     * 该台账记录的生成时间，通常在加班申请审批通过后自动写入
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
