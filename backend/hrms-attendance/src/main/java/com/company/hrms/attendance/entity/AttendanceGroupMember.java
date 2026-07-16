package com.company.hrms.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 员工-考勤组映射（物化）
 * DDL: attendance_group_member (#42)
 * 以 employee_id 为主键，一个员工只能属于一个考勤组
 */
@Data
@TableName("attendance_group_member")
public class AttendanceGroupMember {

    /**
     * 考勤组ID
     * <p>关联 attendance_group 表的主键，标识该员工所属的考勤组。</p>
     * 一个考勤组定义了员工的打卡规则、假期模板、计算周期等配置。
     * 该字段为逻辑外键，与 employee_id 共同构成映射关系。
     */
    @TableField("group_id")
    private Long groupId;

    /**
     * 员工ID（主键）
     * <p>关联 employee 表的主键，唯一标识一名员工。</p>
     * 由于一个员工只能属于一个考勤组，该字段同时作为本表的主键，
     * 确保员工与考勤组之间为一对一映射关系。
     */
    @TableId(type = IdType.INPUT)
    @TableField("employee_id")
    private Long employeeId;
}
