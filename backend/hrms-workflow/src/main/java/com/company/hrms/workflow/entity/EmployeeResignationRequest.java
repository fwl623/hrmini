package com.company.hrms.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 表 #40 employee_resignation_request
 */
@Data
@TableName("employee_resignation_request")
public class EmployeeResignationRequest {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long instanceId;
    private Long employeeId;
    private String status;
    private LocalDate expectedResignDate;
    private String reasonCategory;
    private String resignationType;
    private String reasonDetail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
