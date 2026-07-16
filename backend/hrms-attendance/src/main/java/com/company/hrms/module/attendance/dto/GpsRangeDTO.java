package com.company.hrms.module.attendance.dto;

import lombok.Data;

/**
 * GPS 范围 DTO
 */
@Data
public class GpsRangeDTO {
    private Double lat;
    private Double lng;
    private Integer radiusM;
}
