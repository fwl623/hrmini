package com.company.hrms.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 表 #37 regularization_application
 */
@Data
@TableName("regularization_application")
public class RegularizationApplication {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long instanceId;
    private Long employeeId;
    private String status;
    private LocalDate probationStartDate;
    private LocalDate probationEndDate;
    private String performanceEvaluation;
    private BigDecimal salaryAdjustment;
    private String approvalResult;
    private Integer extendMonths;
    private Long createdBy;
    private LocalDateTime createdAt;
}
