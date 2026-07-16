package com.company.hrms.employee.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 员工详情
 * GET /api/v1/employees/{id}
 */
@Data
public class EmployeeDetailVO {
    private Long employeeId;
    private String empNo;
    private String name;
    private String gender;
    private String mobile;
    private String email;
    private Long departmentId;
    private String department;
    private Long positionId;
    private String position;
    private String grade;
    private Long managerId;
    private String managerName;
    private String workLocation;
    private String employmentType;
    private String employmentStatus;
    private LocalDate hireDate;
    private BigDecimal probationPayRatio;
    // 个人信息
    private String idNumber;
    private LocalDate birthday;
    private String householdAddress;
    private String residenceAddress;
    private String emergencyContact;
    private String emergencyPhone;
    // 银行卡
    private String bankAccount;
    private String bankName;
    // 权限元信息
    private Map<String, String> fieldPermissions;
}
