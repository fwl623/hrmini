package com.company.hrms.module.attendance.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 加班申请 VO
 */
@Data
public class OvertimeApplicationVO {
    private Long id;
    private String employeeName;
    private String overtimeDate;
    private BigDecimal hours;
    private String status;
}
