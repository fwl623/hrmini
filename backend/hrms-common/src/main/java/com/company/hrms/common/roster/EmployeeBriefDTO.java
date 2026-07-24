package com.company.hrms.common.roster;

import lombok.Data;

/**
 * 跨模块只读员工摘要（AI 查数卡片等），避免依赖 employee 模块 VO。
 */
@Data
public class EmployeeBriefDTO {
    private Long employeeId;
    private String empNo;
    private String name;
    private String department;
    private String position;
    private String grade;
    private String employmentStatus;
}
