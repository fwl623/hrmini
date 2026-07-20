package com.company.hrms.module.attendance.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 加班台账 VO
 */
@Data
public class OvertimeLedgerVO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String departmentName;
    private String period;
    private BigDecimal totalHours;
    private Integer rateType;
    private String ledgerDate;
    private String createdAt;
}
