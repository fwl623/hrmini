package com.company.hrms.payroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 批次核算明细表
 *
 * 记录每位员工在每个核算批次中的薪资计算结果。
 * 核心字段包括应发/实发金额、明细JSON、异常标记等。
 *
 * detail_json 示例：
 * [{"itemCode":"BASE_PAY","itemName":"基本工资","amount":15000,"type":"EARNING"},
 *  {"itemCode":"SS_DEDUCT","itemName":"社保扣款","amount":-1050,"type":"DEDUCTION"},
 *  {"itemCode":"TAX","itemName":"个人所得税","amount":-337.5,"type":"DEDUCTION"}]
 *
 * anomaly_flags 示例：["LEAVE_HIGH","SALARY_CHANGE_HIGH"]
 *
 * 手工调整（manual_adjusted=1）后，HR 可区分系统计算值与人工干预值。
 */
@Data
@TableName("payroll_detail")
public class PayrollDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 批次 ID，关联 payroll_batch.id */
    @TableField("batch_id")
    private Long batchId;

    /** 员工 ID，关联 employee.id */
    @TableField("employee_id")
    private Long employeeId;

    /** 核算状态：SUCCESS（成功）/ FAILED（失败） */
    @TableField("calc_status")
    private String calcStatus;

    /** 应发工资（税前），保留 2 位小数 */
    @TableField("gross_salary")
    private BigDecimal grossSalary;

    /** 实发工资（税后），保留 2 位小数 */
    @TableField("net_salary")
    private BigDecimal netSalary;

    /**
     * 各薪资项明细（JSON 数组）
     * 每项含 itemCode, itemName, amount, type(EARNING/DEDUCTION)
     */
    @TableField("detail_json")
    private String detailJson;

    /**
     * 异常标记数组（JSON）
     * 如 ["LEAVE_HIGH","OVERTIME_HIGH","SALARY_CHANGE_HIGH"]
     */
    @TableField("anomaly_flags")
    private String anomalyFlags;

    /** 上月实发工资（用于环比波动检测） */
    @TableField("prev_net_salary")
    private BigDecimal prevNetSalary;

    /** 是否手工调整：0=系统核算, 1=已手工修改 */
    @TableField("manual_adjusted")
    private Integer manualAdjusted;
}
