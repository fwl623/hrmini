package com.company.hrms.employee.entity;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工个人信息表（含敏感加密字段）
 * DDL: #24 employee_personal
 */
@Data
public class EmployeePersonal {
    private Long employeeId;
    private String idNumberEnc;      // AES-256-GCM 密文
    private String idNumberHash;     // SHA-256 哈希
    private LocalDate birthday;
    private String householdAddress;
    private String residenceAddress;
    private String emergencyContact;
    private String emergencyPhone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
