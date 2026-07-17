package com.company.hrms.module.attendance.dto;

import lombok.Data;

/**
 * 弹性班次范围 DTO
 */
@Data
public class FlexibleRangeDTO {
    private String earliest;  // HH:mm
    private String latest;    // HH:mm
}
