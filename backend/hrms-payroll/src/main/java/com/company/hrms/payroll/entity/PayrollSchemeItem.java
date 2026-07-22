package com.company.hrms.payroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 账套工资项目表
 *
 * 定义薪资账套中的每个工资项目及其计算规则。
 * 由 CalculateService.calcItem() 按 item_type 分发到不同计算逻辑。
 *
 * item_type 说明：
 *   FIXED             - 固定项目（基本工资、岗位津贴），金额固定或从档案读取
 *   VARIABLE          - 变动项目（绩效奖金、加班费），每期金额可变
 *   ATTENDANCE_DEDUCT - 考勤扣款（迟到扣款、请假扣款），根据考勤数据计算
 *   SS_DEDUCT         - 社保扣除，按社保基数×比例计算
 *   HF_DEDUCT         - 公积金扣除，按公积金基数×比例计算
 *   TAX               - 个税扣除，累计预扣法计算
 *
 * calc_rule 为 SpEL 表达式（预留，当前由 Java 代码硬编码分发）。
 * 排序号 sort_order 控制工资单展示顺序。
 */
@Data
@TableName("payroll_scheme_item")
public class PayrollSchemeItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 账套 ID，关联 payroll_scheme.id */
    @TableField("scheme_id")
    private Long schemeId;

    /** 项目编码（业务唯一标识），如 BASE_PAY, PERFORMANCE_BONUS */
    @TableField("item_code")
    private String itemCode;

    /** 项目名称（展示用），如"基本工资"、"绩效奖金" */
    @TableField("item_name")
    private String itemName;

    /**
     * 项目类型
     * FIXED / VARIABLE / ATTENDANCE_DEDUCT / SS_DEDUCT / HF_DEDUCT / TAX
     */
    @TableField("item_type")
    private String itemType;

    /**
     * 计算规则（SpEL 表达式，预留）
     * 例如：#BASE_PAY + #PERFORMANCE_BONUS
     */
    @TableField("calc_rule")
    private String calcRule;

    /**
     * 基数类型
     * 如 ssBase（社保基数）、hfBase（公积金基数）
     */
    @TableField("base_field")
    private String baseField;

    /** 比例值（如 0.08 = 8%），配合 baseField 使用 */
    @TableField("ratio")
    private BigDecimal ratio;

    /** 排序号，数字越小越靠前 */
    @TableField("sort_order")
    private Integer sortOrder;
}
