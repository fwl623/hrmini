package com.company.hrms.employee.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 入职确认建档命令（由 workflow confirm 传入申请快照）。
 */
@Data
public class OnboardingArchiveCommand {
    private Long applicationId;
    private LocalDate actualOnboardDate;
    private String name;
    private String gender;
    private String mobile;
    private String email;
    private String idNumber;
    private Long departmentId;
    private Long positionId;
    private String employmentType;
    private Integer probationMonths;
    private BigDecimal probationSalaryRatio;
    private BigDecimal baseSalary;
    private Long managerId;
    private LocalDate expectedOnboardDate;
}
