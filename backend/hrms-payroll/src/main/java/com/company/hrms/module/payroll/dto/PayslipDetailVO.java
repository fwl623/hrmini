package com.company.hrms.module.payroll.dto;

import lombok.Data;
import java.util.List;

@Data
public class PayslipDetailVO {
    private String period;
    private EmployeeInfo employee;
    private List<PayslipItemVO> earnings;
    private double grossSalary;
    private List<PayslipItemVO> deductions;
    private double totalDeduction;
    private double netSalary;

    @Data
    public static class EmployeeInfo {
        private String name;
        private String employeeNo;
        private String department;
    }
}
