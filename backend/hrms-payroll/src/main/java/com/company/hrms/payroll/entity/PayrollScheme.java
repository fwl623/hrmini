package com.company.hrms.payroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 薪资账套
 * DDL: payroll_scheme (#6)
 */
@Data
@TableName("payroll_scheme")
public class PayrollScheme {

    /**
     * 账套ID，主键
     * 自增主键，唯一标识一个薪资账套
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 账套名称
     * 薪资账套的显示名称，例如"2024年度标准账套"、"高管薪酬账套"
     */
    @TableField("name")
    private String name;

    /**
     * 账套描述
     * 用于说明该账套的适用范围、计算规则等附加说明信息
     */
    @TableField("description")
    private String description;

    /**
     * 生效日期
     * 格式：yyyy-MM-dd
     * 标识该薪资账套从何时开始生效执行
     */
    @TableField("effective_date")
    private LocalDate effectiveDate;

    /**
     * 账套状态
     * 可选值：
     * <ul>
     *   <li><b>enabled</b> - 启用，账套可被引用和使用</li>
     *   <li><b>disabled</b> - 停用，账套不可被引用和使用</li>
     * </ul>
     */
    @TableField("status")
    private String status;

    /**
     * 逻辑删除标记
     * 可选值：
     * <ul>
     *   <li><b>0</b> - 未删除（正常）</li>
     *   <li><b>1</b> - 已删除</li>
     * </ul>
     */
    @TableField("deleted")
    private Integer deleted;

    /**
     * 创建时间
     * 格式：yyyy-MM-dd HH:mm:ss
     * 记录该账套记录的创建时间戳
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 最后更新时间
     * 格式：yyyy-MM-dd HH:mm:ss
     * 记录该账套记录最近一次被修改的时间戳
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
