package com.company.hrms.module.auth.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * GET /analytics/overview 响应：人力资源数据概览。
 */
public class AnalyticsOverviewVO {

    private String from;
    private String to;
    private List<HeadcountTrendPoint> headcountTrend = new ArrayList<>();
    private List<DeptDistItem> deptDistribution = new ArrayList<>();
    private List<WorkflowThroughputItem> workflowThroughput = new ArrayList<>();
    private List<CostTrendItem> costTrend = new ArrayList<>();
    private List<KpiRow> kpiTable = new ArrayList<>();

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public List<HeadcountTrendPoint> getHeadcountTrend() {
        return headcountTrend;
    }

    public void setHeadcountTrend(List<HeadcountTrendPoint> headcountTrend) {
        this.headcountTrend = headcountTrend != null ? headcountTrend : new ArrayList<>();
    }

    public List<DeptDistItem> getDeptDistribution() {
        return deptDistribution;
    }

    public void setDeptDistribution(List<DeptDistItem> deptDistribution) {
        this.deptDistribution = deptDistribution != null ? deptDistribution : new ArrayList<>();
    }

    public List<WorkflowThroughputItem> getWorkflowThroughput() {
        return workflowThroughput;
    }

    public void setWorkflowThroughput(List<WorkflowThroughputItem> workflowThroughput) {
        this.workflowThroughput = workflowThroughput != null ? workflowThroughput : new ArrayList<>();
    }

    public List<CostTrendItem> getCostTrend() {
        return costTrend;
    }

    public void setCostTrend(List<CostTrendItem> costTrend) {
        this.costTrend = costTrend != null ? costTrend : new ArrayList<>();
    }

    public List<KpiRow> getKpiTable() {
        return kpiTable;
    }

    public void setKpiTable(List<KpiRow> kpiTable) {
        this.kpiTable = kpiTable != null ? kpiTable : new ArrayList<>();
    }

    public static class HeadcountTrendPoint {
        private String date;
        private long hires;
        private long resignations;

        public HeadcountTrendPoint() {
        }

        public HeadcountTrendPoint(String date, long hires, long resignations) {
            this.date = date;
            this.hires = hires;
            this.resignations = resignations;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public long getHires() {
            return hires;
        }

        public void setHires(long hires) {
            this.hires = hires;
        }

        public long getResignations() {
            return resignations;
        }

        public void setResignations(long resignations) {
            this.resignations = resignations;
        }
    }

    public static class DeptDistItem {
        private String deptName;
        private long headcount;

        public DeptDistItem() {
        }

        public DeptDistItem(String deptName, long headcount) {
            this.deptName = deptName;
            this.headcount = headcount;
        }

        public String getDeptName() {
            return deptName;
        }

        public void setDeptName(String deptName) {
            this.deptName = deptName;
        }

        public long getHeadcount() {
            return headcount;
        }

        public void setHeadcount(long headcount) {
            this.headcount = headcount;
        }
    }

    public static class WorkflowThroughputItem {
        private String processType;
        private String label;
        private long submitted;
        private long approved;

        public String getProcessType() {
            return processType;
        }

        public void setProcessType(String processType) {
            this.processType = processType;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public long getSubmitted() {
            return submitted;
        }

        public void setSubmitted(long submitted) {
            this.submitted = submitted;
        }

        public long getApproved() {
            return approved;
        }

        public void setApproved(long approved) {
            this.approved = approved;
        }
    }

    public static class CostTrendItem {
        private String period;
        private double netTotal;

        public CostTrendItem() {
        }

        public CostTrendItem(String period, double netTotal) {
            this.period = period;
            this.netTotal = netTotal;
        }

        public String getPeriod() {
            return period;
        }

        public void setPeriod(String period) {
            this.period = period;
        }

        public double getNetTotal() {
            return netTotal;
        }

        public void setNetTotal(double netTotal) {
            this.netTotal = netTotal;
        }
    }

    public static class KpiRow {
        private String metric;
        private String value;
        private Double changeRate;
        private String trend;

        public KpiRow() {
        }

        public KpiRow(String metric, String value, Double changeRate, String trend) {
            this.metric = metric;
            this.value = value;
            this.changeRate = changeRate;
            this.trend = trend;
        }

        public String getMetric() {
            return metric;
        }

        public void setMetric(String metric) {
            this.metric = metric;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public Double getChangeRate() {
            return changeRate;
        }

        public void setChangeRate(Double changeRate) {
            this.changeRate = changeRate;
        }

        public String getTrend() {
            return trend;
        }

        public void setTrend(String trend) {
            this.trend = trend;
        }
    }
}
