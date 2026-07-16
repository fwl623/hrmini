package com.company.hrms.payroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 个税累计预扣记录
 * DDL: pay_tax_ytd_record (#52)
 */
@Data
@TableName("pay_tax_ytd_record")
public class PayTaxYtdRecord {

    /**
     * 记录主键ID，自增生成
     * 唯一标识每条个税累计预扣记录
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 员工ID，关联 employee 表主键
     * 用于标识该预扣记录所属的员工
     */
    @TableField("employee_id")
    private Long employeeId;

    /**
     * 账期（纳税所属期）
     * 格式：yyyy-MM，例如 2025-01
     * 表示该记录对应的税款所属月份
     */
    @TableField("period")
    private String period;

    /**
     * 应纳税所得额
     * 单位：元，保留两位小数
     * 计算公式：累计收入 - 累计免税收入 - 累计减除费用 - 累计专项扣除 - 累计专项附加扣除 - 累计依法确定的其他扣除
     * 根据《个人所得税法》规定，居民个人取得综合所得按纳税年度合并计算个人所得税
     */
    @TableField("taxable_income")
    private BigDecimal taxableIncome;

    /**
     * 本期预扣税额
     * 单位：元，保留两位小数
     * 指当前账期实际预扣的个人所得税金额
     * 计算公式：本期预扣税额 = 累计预扣税额 - 上月累计预扣税额（即 cumulativeTax - 上月 cumulativeTax）
     */
    @TableField("tax_deducted")
    private BigDecimal taxDeducted;

    /**
     * 累计预扣税额（年度累计）
     * 单位：元，保留两位小数
     * 指本年度截至当前账期累计预扣的个人所得税总额
     * 该值逐月累加，用于年度汇算清缴时的数据核对
     */
    @TableField("cumulative_tax")
    private BigDecimal cumulativeTax;
}
