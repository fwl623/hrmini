package com.company.hrms.payroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 账套适用范围
 * DDL: payroll_scheme_scope (#8)
 */
@Data
@TableName("payroll_scheme_scope")
public class PayrollSchemeScope {

    /**
     * 主键ID，自增
     * 唯一标识每一条账套适用范围记录
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 账套ID，关联 payroll_scheme 表的主键
     * 标识当前适用范围所属的薪资账套
     */
    @TableField("scheme_id")
    private Long schemeId;

    /**
     * 适用范围类型
     * 可选值：
     * <ul>
     *   <li><b>DEPARTMENT</b> - 部门范围，scope_id 填写部门ID</li>
     *   <li><b>POSITION</b>   - 岗位范围，scope_id 填写岗位ID</li>
     *   <li><b>JOB_LEVEL</b>  - 职级范围，scope_id 填写职级ID</li>
     * </ul>
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 适用范围对象ID
     * 根据 scope_type 的值分别关联 department/position/job_level 表的对应主键
     * 例如：scope_type = DEPARTMENT 时此处填写部门ID
     */
    @TableField("scope_id")
    private String scopeId;
}
