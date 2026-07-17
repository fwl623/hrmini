package com.company.hrms.module.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 补卡配额 VO
 */
@Data
@AllArgsConstructor
public class QuotaVO {
    private int totalQuota;
    private int usedQuota;
    private int remainingQuota;
}
