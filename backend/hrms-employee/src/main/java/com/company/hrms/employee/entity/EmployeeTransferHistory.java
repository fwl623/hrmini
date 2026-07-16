package com.company.hrms.employee.entity;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 调岗历史表
 * DDL: #39 employee_transfer_history
 */
@Data
public class EmployeeTransferHistory {
    private Long id;
    private Long employeeId;
    private Long transferAppId;
    private Long fromDepartmentId;
    private Long toDepartmentId;
    private Long fromPositionId;
    private Long toPositionId;
    private LocalDate transferDate;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
