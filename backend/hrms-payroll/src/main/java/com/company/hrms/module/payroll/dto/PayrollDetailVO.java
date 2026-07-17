package com.company.hrms.module.payroll.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/* PayrollDetailVO */
@Data
public class PayrollDetailVO {
    private Long employeeId;
    private String employeeName;
    private BigDecimal grossSalary;
    private BigDecimal netSalary;
    private String calcStatus;
    private List<String> anomalyFlags;
    private boolean manualAdjusted;
}
