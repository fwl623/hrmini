package com.company.hrms.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 表 #38 transfer_application
 */
@Data
@TableName("transfer_application")
public class TransferApplication {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long instanceId;
    private Long employeeId;
    private String status;
    private Long fromDepartmentId;
    private Long newDepartmentId;
    private Long newPositionId;
    private String newJobLevel;
    private Long newManagerId;
    private BigDecimal salaryAdjustment;
    private LocalDate effectiveDate;
    private String reason;
    private Long createdBy;
    private LocalDateTime createdAt;
}
