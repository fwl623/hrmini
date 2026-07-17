package com.company.hrms.module.payroll.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/* ChartDataVO */
@Data
public class ChartDataVO {
    private List<CostTrendItem> costTrend = new ArrayList<>();
    private List<DeptDistItem> deptDistribution = new ArrayList<>();

    @Data
    public static class CostTrendItem {
        private String period;
        private double grossTotal;
    }

    @Data
    public static class DeptDistItem {
        private String deptName;
        private double grossTotal;
    }
}
