package com.company.hrms.module.org.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工号复用历史（org 模块自管，供 EmployeeIdGenerator 使用）。
 */
@Data
@TableName("employee_no_history")
public class EmployeeNoHistory {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("employee_no")
    private String employeeNo;
    private String year;
    @TableField("dept_code")
    private String deptCode;
    @TableField("employee_id")
    private Long employeeId;
    /** 0=占用中 1=可复用 */
    @TableField("reuse_flag")
    private Integer reuseFlag;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
