package com.company.hrms.module.auth.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.common.approval.ApprovalEngineService;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.module.auth.dto.WorkbenchSummaryVO;
import com.company.hrms.module.auth.entity.OperationLog;
import com.company.hrms.module.auth.mapper.OperationLogMapper;
import com.company.hrms.module.auth.mapper.WorkbenchMapper;
import com.company.hrms.module.auth.service.WorkbenchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkbenchServiceImpl implements WorkbenchService {

    private static final Logger log = LoggerFactory.getLogger(WorkbenchServiceImpl.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final WorkbenchMapper workbenchMapper;
    private final OperationLogMapper operationLogMapper;
    private final ObjectProvider<ApprovalEngineService> approvalEngineService;

    public WorkbenchServiceImpl(
            WorkbenchMapper workbenchMapper,
            OperationLogMapper operationLogMapper,
            ObjectProvider<ApprovalEngineService> approvalEngineService) {
        this.workbenchMapper = workbenchMapper;
        this.operationLogMapper = operationLogMapper;
        this.approvalEngineService = approvalEngineService;
    }

    @Override
    public WorkbenchSummaryVO summary() {
        WorkbenchSummaryVO vo = new WorkbenchSummaryVO();
        vo.setTotalEmployees(safeLong(() -> workbenchMapper.countActiveEmployees()));
        vo.setNewHiresThisMonth(safeLong(() -> workbenchMapper.countNewHiresThisMonth()));
        // 与审批中心「待办」同口径：仅当前登录用户作为有效审批人的待办，禁止全库串数
        vo.setPendingApprovals(countMyPendingApprovals());
        vo.setAttendanceAnomalies(safeLong(() -> workbenchMapper.countAttendanceAnomaliesToday()));
        vo.setTodayPunchRate(safePunchRate());
        vo.setDepartmentStats(safeList(() -> workbenchMapper.listDepartmentStats()));
        vo.setVisitTrend(buildVisitTrend());
        vo.setRecentOperations(loadRecentOperations());
        return vo;
    }

    private long countMyPendingApprovals() {
        try {
            ApprovalEngineService engine = approvalEngineService.getIfAvailable();
            if (engine == null) {
                log.warn("workbench pendingApprovals degraded: ApprovalEngineService unavailable");
                return 0L;
            }
            Long userId = SecurityUtils.getUserId();
            if (userId == null) {
                return 0L;
            }
            return engine.countPendingTasksForAssignee(userId);
        } catch (Exception e) {
            log.warn("workbench pendingApprovals degraded: {}", e.getMessage());
            return 0L;
        }
    }

    private Double safePunchRate() {
        try {
            Map<String, Object> stats = workbenchMapper.todayPunchStats();
            if (stats == null || stats.isEmpty()) {
                return null;
            }
            long punched = toLong(firstValue(stats, "punched", "PUNCHED"));
            long total = toLong(firstValue(stats, "total", "TOTAL"));
            if (total <= 0) {
                return 0D;
            }
            return Math.round(punched * 10000.0 / total) / 10000.0;
        } catch (Exception e) {
            log.warn("workbench punch rate degraded: {}", e.getMessage());
            return null;
        }
    }

    private List<WorkbenchSummaryVO.VisitTrendPoint> buildVisitTrend() {
        Map<String, Long> byDay = new HashMap<>();
        try {
            List<Map<String, Object>> rows = workbenchMapper.listLoginTrendLast7Days();
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    Object date = firstValue(row, "date", "DATE");
                    Object cnt = firstValue(row, "cnt", "CNT", "count", "COUNT");
                    if (date != null) {
                        byDay.put(String.valueOf(date), toLong(cnt));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("workbench visit trend degraded: {}", e.getMessage());
        }
        List<WorkbenchSummaryVO.VisitTrendPoint> points = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(6);
        for (int i = 0; i < 7; i++) {
            String d = start.plusDays(i).format(DAY);
            points.add(new WorkbenchSummaryVO.VisitTrendPoint(d, byDay.getOrDefault(d, 0L)));
        }
        return points;
    }

    private List<WorkbenchSummaryVO.RecentOperationVO> loadRecentOperations() {
        try {
            List<OperationLog> logs = operationLogMapper.selectList(
                    new LambdaQueryWrapper<OperationLog>()
                            .orderByDesc(OperationLog::getCreatedAt)
                            .last("LIMIT 10"));
            List<WorkbenchSummaryVO.RecentOperationVO> list = new ArrayList<>();
            for (OperationLog logRow : logs) {
                WorkbenchSummaryVO.RecentOperationVO item = new WorkbenchSummaryVO.RecentOperationVO();
                item.setId(logRow.getId());
                item.setUserId(logRow.getUserId());
                item.setModule(logRow.getModule());
                item.setAction(logRow.getAction());
                item.setTargetId(logRow.getTargetId());
                item.setCreatedAt(logRow.getCreatedAt() == null ? null : logRow.getCreatedAt().toString());
                list.add(item);
            }
            return list;
        } catch (Exception e) {
            log.warn("workbench recent ops degraded: {}", e.getMessage());
            return List.of();
        }
    }

    private long safeLong(LongSupplier supplier) {
        try {
            Long v = supplier.get();
            return v == null ? 0L : v;
        } catch (Exception e) {
            log.warn("workbench kpi degraded: {}", e.getMessage());
            return 0L;
        }
    }

    private <T> List<T> safeList(ListSupplier<T> supplier) {
        try {
            List<T> list = supplier.get();
            return list == null ? List.of() : list;
        } catch (Exception e) {
            log.warn("workbench list degraded: {}", e.getMessage());
            return List.of();
        }
    }

    private static Object firstValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @FunctionalInterface
    private interface LongSupplier {
        Long get();
    }

    @FunctionalInterface
    private interface ListSupplier<T> {
        List<T> get();
    }
}
