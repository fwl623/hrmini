package com.company.hrms.module.auth.dto;

import java.util.ArrayList;
import java.util.List;

/** GET /workbench/summary 响应 */
public class WorkbenchSummaryVO {

    private long totalEmployees;
    private long newHiresThisMonth;
    /** 当前用户待办数（与审批中心 pending 同口径，非全库） */
    private long pendingApprovals;
    private long attendanceAnomalies;
    private Double todayPunchRate;
    private List<DeptHeadcountStat> departmentStats = new ArrayList<>();
    /** 近 7 日登录成功次数（访问趋势降级数据源） */
    private List<VisitTrendPoint> visitTrend = new ArrayList<>();
    /** 最近操作（最多 10 条） */
    private List<RecentOperationVO> recentOperations = new ArrayList<>();

    public long getTotalEmployees() {
        return totalEmployees;
    }

    public void setTotalEmployees(long totalEmployees) {
        this.totalEmployees = totalEmployees;
    }

    public long getNewHiresThisMonth() {
        return newHiresThisMonth;
    }

    public void setNewHiresThisMonth(long newHiresThisMonth) {
        this.newHiresThisMonth = newHiresThisMonth;
    }

    public long getPendingApprovals() {
        return pendingApprovals;
    }

    public void setPendingApprovals(long pendingApprovals) {
        this.pendingApprovals = pendingApprovals;
    }

    public long getAttendanceAnomalies() {
        return attendanceAnomalies;
    }

    public void setAttendanceAnomalies(long attendanceAnomalies) {
        this.attendanceAnomalies = attendanceAnomalies;
    }

    public Double getTodayPunchRate() {
        return todayPunchRate;
    }

    public void setTodayPunchRate(Double todayPunchRate) {
        this.todayPunchRate = todayPunchRate;
    }

    public List<DeptHeadcountStat> getDepartmentStats() {
        return departmentStats;
    }

    public void setDepartmentStats(List<DeptHeadcountStat> departmentStats) {
        this.departmentStats = departmentStats != null ? departmentStats : new ArrayList<>();
    }

    public List<VisitTrendPoint> getVisitTrend() {
        return visitTrend;
    }

    public void setVisitTrend(List<VisitTrendPoint> visitTrend) {
        this.visitTrend = visitTrend != null ? visitTrend : new ArrayList<>();
    }

    public List<RecentOperationVO> getRecentOperations() {
        return recentOperations;
    }

    public void setRecentOperations(List<RecentOperationVO> recentOperations) {
        this.recentOperations = recentOperations != null ? recentOperations : new ArrayList<>();
    }

    public static class DeptHeadcountStat {
        private String deptName;
        private long headcount;

        public DeptHeadcountStat() {
        }

        public DeptHeadcountStat(String deptName, long headcount) {
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

    public static class VisitTrendPoint {
        private String date;
        private long count;

        public VisitTrendPoint() {
        }

        public VisitTrendPoint(String date, long count) {
            this.date = date;
            this.count = count;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }
    }

    public static class RecentOperationVO {
        private Long id;
        private Long userId;
        private String module;
        private String action;
        private String targetId;
        private String createdAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getModule() {
            return module;
        }

        public void setModule(String module) {
            this.module = module;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getTargetId() {
            return targetId;
        }

        public void setTargetId(String targetId) {
            this.targetId = targetId;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }
}
