package com.company.hrms.payroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 个税税率表（累计预扣法）
 * <p>采用七级超额累进税率，按年度累计应纳税所得额查表计算。</p>
 * <p>税率区间：3% ~ 45%，对应不同的应纳税所得额范围。</p>
 * <p>计算公式：本期应预扣税额 = (累计收入 - 累计免税收入 - 累计减除费用 - 累计专项扣除 - 累计专项附加扣除) × 税率 - 速算扣除数 - 累计已预扣税额</p>
 * DDL: pay_tax_bracket (#9)
 */
@Data
@TableName("pay_tax_bracket")
public class PayTaxBracket {

    /**
     * 主键 ID，自增
     * 唯一标识一条税率记录
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 纳税年度
     * 格式：yyyy，例如 2024 表示 2024 纳税年度
     * 税率表按年度适用，年度内固定
     */
    @TableField("tax_year")
    private Integer taxYear;

    /**
     * 应纳税所得额下限（起征金额）
     * 单位：元，含本数
     * 该税率档次的最低应纳税所得额
     */
    @TableField("min_taxable")
    private BigDecimal minTaxable;

    /**
     * 应纳税所得额上限
     * 单位：元，不含本数
     * NULL 表示上不封顶（最高档税率）
     */
    @TableField("max_taxable")
    private BigDecimal maxTaxable;

    /**
     * 适用税率
     * 例如：0.03 表示 3%，0.45 表示 45%
     * 随应纳税所得额增加而递增
     */
    @TableField("rate")
    private BigDecimal rate;

    /**
     * 速算扣除数
     * 单位：元
     * 为简化计算而设置的固定扣除额，用于快速计算应纳税额
     */
    @TableField("quick_deduction")
    private BigDecimal quickDeduction;
}
