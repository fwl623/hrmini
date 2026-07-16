package com.company.hrms.module.attendance.dto;

import lombok.Data;

import java.util.List;

/**
 * 月汇总 VO
 */
@Data
public class MonthlySummaryVO {
    private List<MonthlySummaryItem> list;
    private long total;
    private boolean locked;
}
