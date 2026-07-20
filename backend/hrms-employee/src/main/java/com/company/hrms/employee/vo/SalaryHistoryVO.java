package com.company.hrms.employee.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 调薪历史展示。
 */
@Data
public class SalaryHistoryVO {
    private Long id;
    private Long employeeId;
    /** 变更字段名，如 baseSalary */
    private String fieldName;
    private BigDecimal oldValue;
    private BigDecimal newValue;
    private LocalDate effectiveDate;
    private String reason;
    /** 操作人 userId */
    private Long operatorId;
    private LocalDateTime createdAt;
}
