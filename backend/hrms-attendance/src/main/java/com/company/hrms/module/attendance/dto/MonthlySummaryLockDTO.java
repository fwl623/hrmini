package com.company.hrms.module.attendance.dto;

import lombok.Data;

/**
 * 月汇总锁定 DTO
 */
@Data
public class MonthlySummaryLockDTO {
    private String period;
    private Boolean locked;
}
