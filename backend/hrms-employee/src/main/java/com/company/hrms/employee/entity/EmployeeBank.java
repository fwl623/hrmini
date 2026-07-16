package com.company.hrms.employee.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 员工银行卡信息表
 * DDL: #26 employee_bank
 */
@Data
public class EmployeeBank {
    private Long employeeId;
    private String bankAccountEnc;   // AES-256-GCM 密文
    private String bankAccountTail;  // 后四位
    private String bankName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
