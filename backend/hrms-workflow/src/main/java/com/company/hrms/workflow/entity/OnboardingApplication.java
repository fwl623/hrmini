package com.company.hrms.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 表 #36 onboarding_application
 */
@Data
@TableName("onboarding_application")
public class OnboardingApplication {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long instanceId;
    private String status;
    private String name;
    private String gender;
    private String mobile;
    private String email;
    private String idNumberEnc;
    private String idNumberHash;
    private LocalDate expectedOnboardDate;
    private Long departmentId;
    private Long positionId;
    private String employmentType;
    private Integer probationMonths;
    private BigDecimal probationSalaryRatio;
    private BigDecimal baseSalary;
    private LocalDate actualOnboardDate;
    private Long managerId;
    private Long employeeId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
