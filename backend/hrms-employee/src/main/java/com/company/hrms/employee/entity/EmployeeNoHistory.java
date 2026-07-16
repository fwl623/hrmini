package com.company.hrms.employee.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 工号复用历史表
 * DDL: #28 employee_no_history
 */
@Data
public class EmployeeNoHistory {
    private Long id;
    private String employeeNo;
    private String year;
    private String deptCode;
    private Long employeeId;
    private Integer reuseFlag;       // 0=占用中 1=可复用
    private LocalDateTime createdAt;
}
