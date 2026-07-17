package com.company.hrms.module.payroll.dto;

import lombok.Data;

@Data
public class PayslipVO {
    private Long employeeId;
    private String employeeName;
    private String period;
    private double grossSalary;
    private double netSalary;
    private String status;
}
