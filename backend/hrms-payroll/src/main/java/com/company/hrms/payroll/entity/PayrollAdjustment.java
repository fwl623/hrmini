package com.company.hrms.payroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 核算手动调整记录
 * DDL: payroll_adjustment (#55)
 */
@Data
@TableName("payroll_adjustment")
public class PayrollAdjustment {

    /**
     * 主键ID
     * 自增主键，唯一标识一条手动调整记录
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 明细ID
     * 关联核算明细表（payroll_detail）的主键，标识本条调整所属的核算明细
     * 通过该字段与 payroll_detail 建立外键关联
     */
    @TableField("detail_id")
    private Long detailId;

    /**
     * 调整项目编码
     * 标识调整项目的类型，例如：
     * <ul>
     *   <li>BONUS - 奖金</li>
     *   <li>DEDUCTION - 扣款</li>
     *   <li>OVERTIME - 加班费</li>
     *   <li>SUBSIDY - 补贴</li>
     *   <li>PENALTY - 罚金</li>
     *   <li>OTHER - 其他</li>
     * </ul>
     * 编码体系与薪资核算规则中的项目编码保持一致
     */
    @TableField("item_code")
    private String itemCode;

    /**
     * 调整金额（可为负）
     * 单位：元（人民币），保留两位小数
     * 正数表示增加（如补发、奖金），负数表示减少（如扣款、罚款）
     * 业务含义：在核算明细基础上额外增加或扣减的金额
     */
    @TableField("adjust_amount")
    private BigDecimal adjustAmount;

    /**
     * 调整原因
     * 人工调整的具体原因说明，由操作人填写
     * 例如：漏发餐补、迟到扣款、绩效奖励补发等
     * 用于审计追溯和事后核查
     */
    @TableField("reason")
    private String reason;

    /**
     * 操作人ID
     * 关联员工表（employee）或用户表（user）的主键
     * 标识执行本次调整操作的人员
     */
    @TableField("operator_id")
    private Long operatorId;

    /**
     * 创建时间
     * 格式：yyyy-MM-dd HH:mm:ss
     * 记录本条调整数据的创建时间，由系统自动生成
     * 用于数据审计和调整记录的时间追溯
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
