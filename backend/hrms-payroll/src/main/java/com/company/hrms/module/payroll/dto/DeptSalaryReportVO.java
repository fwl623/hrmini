package com.company.hrms.module.payroll.dto;

import lombok.Data;
import java.util.List;

/**
 * 部门薪资分布报表 VO
 */
@Data
public class DeptSalaryReportVO {
    private Long deptId;
    private String deptName;
    private String period;
    private int totalEmployeeCount;
    private double totalSalary;
    private double totalActualSalary;
    private List<DeptSalaryItem> children;

    @Data
    public static class DeptSalaryItem {
        private Long deptId;
        private String deptName;
        private int employeeCount;
        private double totalSalary;
        private double totalActualSalary;
        /** 是否存在员工薪资核算记录 */
        private boolean hasDetail;
        /** 展开明细时的员工列表 */
        private List<EmployeeDetail> employees;
    }

    @Data
    public static class EmployeeDetail {
        private Long employeeId;
        private String employeeName;
        private String positionName;
        private String hireDate;
        private double grossSalary;
        private double netSalary;
    }
}
