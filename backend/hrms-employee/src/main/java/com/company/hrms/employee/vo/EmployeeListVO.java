package com.company.hrms.employee.vo;

import lombok.Data;
import java.time.LocalDate;

/**
 * 员工列表项
 * GET /api/v1/employees 列表
 */
@Data
public class EmployeeListVO {
    private Long employeeId;
    private String empNo;
    private String name;
    private String department;
    private String position;
    private String grade;
    private String employmentStatus;
    private LocalDate hireDate;
}
