package com.company.hrms.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 考勤组适用人员范围
 * DDL: attendance_group_scope (#23)
 */
@Data
@TableName("attendance_group_scope")
public class AttendanceGroupScope {

    /**
     * 主键ID
     * 自增主键，无业务含义，仅用于唯一标识该行记录
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 考勤组ID，关联 attendance_group 表主键
     * 用于标识该条范围记录所属的考勤组
     */
    @TableField("group_id")
    private Long groupId;

    /**
     * 范围类型，标识 scope_id 所对应的实体类型
     * <ul>
     *   <li>DEPARTMENT — 部门，此时 scope_id 关联 department 表主键</li>
     *   <li>POSITION   — 职位，此时 scope_id 关联 position 表主键</li>
     *   <li>EMPLOYEE   — 员工，此时 scope_id 关联 employee 表主键</li>
     * </ul>
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 范围对象ID，根据 scope_type 的值分别关联不同表的主键
     * <ul>
     *   <li>scope_type = DEPARTMENT 时，关联 department 表主键</li>
     *   <li>scope_type = POSITION   时，关联 position  表主键</li>
     *   <li>scope_type = EMPLOYEE   时，关联 employee  表主键</li>
     * </ul>
     */
    @TableField("scope_id")
    private Long scopeId;
}
