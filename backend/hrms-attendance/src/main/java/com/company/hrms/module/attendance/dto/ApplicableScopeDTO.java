package com.company.hrms.module.attendance.dto;

import lombok.Data;

import java.util.List;

/**
 * 适用范围 DTO
 */
@Data
public class ApplicableScopeDTO {
    private List<Long> departmentIds;
    private List<Long> positionIds;
    private List<Long> employeeIds;
}
