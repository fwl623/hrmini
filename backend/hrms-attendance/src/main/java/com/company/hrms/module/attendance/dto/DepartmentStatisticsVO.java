package com.company.hrms.module.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 部门统计 VO（3 项率）
 */
@Data
@AllArgsConstructor
public class DepartmentStatisticsVO {
    private Long departmentId;
    private String period;
    private double attendanceRate;
    private double lateRate;
    private double leaveRate;
}
