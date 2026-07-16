package com.company.hrms.payroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 账套工资项目
 * DDL: payroll_scheme_item (#7)
 */
@Data
@TableName("payroll_scheme_item")
public class PayrollSchemeItem {

    /**
     * 主键ID，自增
     * 唯一标识一条账套工资项目记录
     * 由数据库自动生成（AUTO递增策略）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 账套ID，关联 payroll_scheme 表主键
     * 标识该工资项目所属的账套方案
     * 外键关联 payroll_scheme.id
     */
    @TableField("scheme_id")
    private Long schemeId;

    /**
     * 项目编码
     * 工资项目的唯一业务编码，用于系统内部识别和公式引用
     * 例如：BASE_PAY, PERFORMANCE, SS_PERSONAL 等
     */
    @TableField("item_code")
    private String itemCode;

    /**
     * 项目名称
     * 工资项目的中文显示名称，用于前端展示和工资单打印
     * 例如：基本工资、绩效奖金、社保个人部分等
     */
    @TableField("item_name")
    private String itemName;

    /**
     * 项目类型
     * 标识工资项目的业务分类，支持以下枚举值：
     * <ul>
     *   <li><b>FIXED</b> — 固定项目（如基本工资、岗位津贴），金额固定或由公式计算</li>
     *   <li><b>VARIABLE</b> — 变动项目（如绩效奖金、加班费），每期金额可变</li>
     *   <li><b>ATTENDANCE_DEDUCT</b> — 考勤扣款（如事假扣款、旷工扣款），根据考勤数据计算</li>
     *   <li><b>SS_DEDUCT</b> — 社保扣除（养老、医疗、失业个人部分），按社保基数×比例计算</li>
     *   <li><b>HF_DEDUCT</b> — 公积金扣除，按公积金基数×比例计算</li>
     *   <li><b>TAX</b> — 个税扣除，根据应纳税所得额与累进税率计算</li>
     * </ul>
     */
    @TableField("item_type")
    private String itemType;

    /**
     * 计算规则，SpEL（Spring Expression Language）表达式
     * 用于描述该工资项目的自动计算逻辑
     * 例如：
     * <ul>
     *   <li>固定值: {@code 5000}</li>
     *   <li>引用其他项目: {@code #BASE_PAY + #PERFORMANCE}</li>
     *   <li>条件计算: {@code #ATTENDANCE_DAYS >= 22 ? #FULL_ATTENDANCE_BONUS : 0}</li>
     *   <li>基数×比例: {@code #ssBase * #SS_PERSONAL_RATIO}</li>
     * </ul>
     * 表达式中以 # 开头的变量引用其他工资项目编码（item_code）
     */
    @TableField("calc_rule")
    private String calcRule;

    /**
     * 基数类型
     * 当 itemType 为 SS_DEDUCT、HF_DEDUCT 或 ATTENDANCE_DEDUCT 时，
     * 指定计算所用的基数来源字段，支持以下枚举值：
     * <ul>
     *   <li><b>ssBase</b> — 社保基数，用于计算社保扣除金额</li>
     *   <li><b>hfBase</b> — 公积金基数，用于计算公积金扣除金额</li>
     *   <li><b>performanceBase</b> — 绩效基数，用于计算绩效相关扣款</li>
     * </ul>
     */
    @TableField("base_field")
    private String baseField;

    /**
     * 比例值（百分比或小数）
     * 当 itemType 为 SS_DEDUCT、HF_DEDUCT 时配合 baseField 使用
     * 例如：0.08 表示 8% 的比例
     * 金额计算公式：基数 × 比例
     * 单位：以小数形式表示的百分比（0.08 = 8%）
     */
    @TableField("ratio")
    private BigDecimal ratio;

    /**
     * 排序号
     * 控制工资项目在工资单中的显示顺序
     * 数字越小越靠前
     */
    @TableField("sort_order")
    private Integer sortOrder;
}
