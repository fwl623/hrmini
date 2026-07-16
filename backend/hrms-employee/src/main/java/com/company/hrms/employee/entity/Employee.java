package com.company.hrms.employee.entity;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工主表
 * DDL: #21 employee
 */
@Data
public class Employee {
    private Long id;
    private String employeeNo;
    private Long userId;
    private String name;
    private String gender;           // MALE/FEMALE
    private String mobile;
    private String email;
    private Long departmentId;
    private Long positionId;
    private String grade;            // 职级，如 P5
    private Long managerId;
    private String workLocation;
    private LocalDate hireDate;
    private String employmentType;   // fulltime/parttime/intern
    private Integer employmentStatus; // 10/20/30/40
    private LocalDate lastWorkDay;
    private java.math.BigDecimal probationPayRatio;
    private LocalDate probationEndDate;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
