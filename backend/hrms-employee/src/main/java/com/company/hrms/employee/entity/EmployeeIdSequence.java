package com.company.hrms.employee.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 工号序列表（Redis 降级兜底）
 * DDL: #27 employee_id_sequence_deprecated
 */
@Data
public class EmployeeIdSequence {
    private Long id;
    private String year;
    private String deptCode;
    private Integer currentVal;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
