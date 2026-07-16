package com.company.hrms.employee.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 手机号变更申请表（个人中心）
 * DDL: #31 employee_mobile_change_application
 */
@Data
public class EmployeeMobileChangeApplication {
    private Long id;
    private Long instanceId;
    private Long employeeId;
    private Long userId;
    private String oldMobile;
    private String newMobile;
    private Integer smsVerified;     // 0=否 1=是
    private String reason;
    private String status;           // PENDING/APPROVED/REJECTED/CANCELLED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
