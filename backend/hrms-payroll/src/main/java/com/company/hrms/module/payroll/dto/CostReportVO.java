package com.company.hrms.module.payroll.dto;

import lombok.Data;
import java.util.List;

/* CostReportVO */
@Data
public class CostReportVO {
    private List<CostTrendItem> trend;
    private List<DeptDistItem> deptDistribution;

    @Data
    public static class CostTrendItem {
        private String period;
        private double grossTotal;
        private double netTotal;
    }

    @Data
    public static class DeptDistItem {
        private String deptName;
        private double grossTotal;
        private double netTotal;
    }
}
