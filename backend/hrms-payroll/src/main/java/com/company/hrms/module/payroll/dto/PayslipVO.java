package com.company.hrms.module.payroll.dto;

import lombok.Data;

/* PayslipVO */
@Data
public class PayslipVO {
    private Long employeeId;
    private String employeeName;
    private Long departmentId;
    private String departmentName;
    private String period;
    private double grossSalary;
    private double netSalary;
    private String status;
}
