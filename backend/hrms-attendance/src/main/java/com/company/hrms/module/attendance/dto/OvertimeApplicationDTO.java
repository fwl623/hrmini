package com.company.hrms.module.attendance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 加班申请 DTO
 */
@Data
public class OvertimeApplicationDTO {
    private String overtimeDate;   // YYYY-MM-DD
    private String startTime;      // HH:mm
    private String endTime;        // HH:mm
    private String reason;
}
