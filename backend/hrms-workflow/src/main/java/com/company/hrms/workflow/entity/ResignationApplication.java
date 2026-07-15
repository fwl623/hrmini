package com.company.hrms.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 表 #41 resignation_application
 */
@Data
@TableName("resignation_application")
public class ResignationApplication {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long instanceId;
    private Long requestId;
    private Long employeeId;
    private String status;
    private LocalDate resignationDate;
    private String reasonCategory;
    private String resignationType;
    private String reasonDetail;
    private Long handoverEmployeeId;
    private Long createdBy;
    private LocalDateTime createdAt;
}
