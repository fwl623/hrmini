package com.company.hrms.module.auth.service.serviceImpl;

import com.company.hrms.module.auth.dto.AnalyticsOverviewVO;
import com.company.hrms.module.auth.mapper.AnalyticsMapper;
import com.company.hrms.module.auth.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsServiceImpl.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AnalyticsMapper analyticsMapper;

    public AnalyticsServiceImpl(AnalyticsMapper analyticsMapper) {
        this.analyticsMapper = analyticsMapper;
    }

    @Override
    public AnalyticsOverviewVO overview(String fromStr, String toStr) {
        LocalDate to = parseDay(toStr, LocalDate.now());
        LocalDate from = parseDay(fromStr, to.minusDays(29));
        if (from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }
        // 防止一次扫太久
        if (ChronoUnit.DAYS.between(from, to) > 366) {
            from = to.minusDays(365);
        }

        String fromS = from.format(DAY);
        String toS = to.format(DAY);
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1);
        String prevFromS = prevFrom.format(DAY);
        String prevToS = prevTo.format(DAY);

        AnalyticsOverviewVO vo = new AnalyticsOverviewVO();
        vo.setFrom(fromS);
        vo.setTo(toS);
        vo.setHeadcountTrend(buildHeadcountTrend(from, to));
        vo.setDeptDistribution(safeDeptDist());
        vo.setWorkflowThroughput(safeWorkflow(fromS, toS));
        vo.setCostTrend(safeCostTrend(from, to));
        vo.setKpiTable(buildKpiTable(fromS, toS, prevFromS, prevToS));
        return vo;
    }

    private List<AnalyticsOverviewVO.HeadcountTrendPoint> buildHeadcountTrend(LocalDate from, LocalDate to) {
        Map<String, Long> hires = toCountMap(safeList(() -> analyticsMapper.listHireTrend(from.format(DAY), to.format(DAY))));
        Map<String, Long> resigns = toCountMap(safeList(() -> analyticsMapper.listResignTrend(from.format(DAY), to.format(DAY))));
        List<AnalyticsOverviewVO.HeadcountTrendPoint> points = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            String key = d.format(DAY);
            points.add(new AnalyticsOverviewVO.HeadcountTrendPoint(
                    key, hires.getOrDefault(key, 0L), resigns.getOrDefault(key, 0L)));
        }
        return points;
    }

    private List<AnalyticsOverviewVO.DeptDistItem> safeDeptDist() {
        List<AnalyticsOverviewVO.DeptDistItem> list = new ArrayList<>();
        for (Map<String, Object> row : safeList(analyticsMapper::listDepartmentDistribution)) {
            String name = str(row.get("deptName"));
            if (!StringUtils.hasText(name)) {
                name = "未命名部门";
            }
            list.add(new AnalyticsOverviewVO.DeptDistItem(name, toLong(row.get("headcount"))));
        }
        return list;
    }

    private List<AnalyticsOverviewVO.WorkflowThroughputItem> safeWorkflow(String fromS, String toS) {
        String fromTs = fromS + " 00:00:00";
        String toTsEx = LocalDate.parse(toS).plusDays(1).format(DAY) + " 00:00:00";
        List<AnalyticsOverviewVO.WorkflowThroughputItem> list = new ArrayList<>();
        for (Map<String, Object> row : safeList(() -> analyticsMapper.listWorkflowThroughput(fromTs, toTsEx))) {
            AnalyticsOverviewVO.WorkflowThroughputItem item = new AnalyticsOverviewVO.WorkflowThroughputItem();
            String type = str(row.get("processType"));
            item.setProcessType(type);
            item.setLabel(labelProcess(type));
            item.setSubmitted(toLong(row.get("submitted")));
            item.setApproved(toLong(row.get("approved")));
            list.add(item);
        }
        return list;
    }

    private List<AnalyticsOverviewVO.CostTrendItem> safeCostTrend(LocalDate from, LocalDate to) {
        String periodFrom = from.format(PERIOD);
        String periodTo = to.format(PERIOD);
        List<AnalyticsOverviewVO.CostTrendItem> list = new ArrayList<>();
        try {
            for (Map<String, Object> row : analyticsMapper.listCostTrend(periodFrom, periodTo)) {
                list.add(new AnalyticsOverviewVO.CostTrendItem(str(row.get("period")), toDouble(row.get("netTotal"))));
            }
        } catch (Exception e) {
            log.warn("analytics cost trend degraded: {}", e.getMessage());
        }
        return list;
    }

    private List<AnalyticsOverviewVO.KpiRow> buildKpiTable(
            String fromS, String toS, String prevFromS, String prevToS) {
        long headcount = safeLong(analyticsMapper::countActiveEmployees);
        long hires = safeLong(() -> analyticsMapper.countHiresBetween(fromS, toS));
        long prevHires = safeLong(() -> analyticsMapper.countHiresBetween(prevFromS, prevToS));
        long resigns = safeLong(() -> analyticsMapper.countResignationsBetween(fromS, toS));
        long prevResigns = safeLong(() -> analyticsMapper.countResignationsBetween(prevFromS, prevToS));

        Double punchRate = null;
        Double prevPunchRate = null;
        try {
            punchRate = calcPunchRate(analyticsMapper.punchStatsBetween(fromS, toS));
            prevPunchRate = calcPunchRate(analyticsMapper.punchStatsBetween(prevFromS, prevToS));
        } catch (Exception e) {
            log.warn("analytics punch rate degraded: {}", e.getMessage());
        }

        Double approveRate = null;
        Double prevApproveRate = null;
        try {
            String fromTs = fromS + " 00:00:00";
            String toTsEx = LocalDate.parse(toS).plusDays(1).format(DAY) + " 00:00:00";
            String prevFromTs = prevFromS + " 00:00:00";
            String prevToTsEx = LocalDate.parse(prevToS).plusDays(1).format(DAY) + " 00:00:00";
            approveRate = calcApproveRate(analyticsMapper.approvalStatsBetween(fromTs, toTsEx));
            prevApproveRate = calcApproveRate(analyticsMapper.approvalStatsBetween(prevFromTs, prevToTsEx));
        } catch (Exception e) {
            log.warn("analytics approval rate degraded: {}", e.getMessage());
        }

        double cost = sumCost(fromS, toS);
        double prevCost = sumCost(prevFromS, prevToS);

        List<AnalyticsOverviewVO.KpiRow> rows = new ArrayList<>();
        rows.add(kpi("在职人数", String.valueOf(headcount), null));
        rows.add(kpi("期间入职", String.valueOf(hires), changeRate(hires, prevHires)));
        rows.add(kpi("期间离职", String.valueOf(resigns), changeRate(resigns, prevResigns)));
        if (punchRate != null) {
            rows.add(kpi("打卡覆盖率", formatPct(punchRate), changeRate(punchRate, prevPunchRate)));
        }
        if (approveRate != null) {
            rows.add(kpi("审批通过率", formatPct(approveRate), changeRate(approveRate, prevApproveRate)));
        }
        if (cost > 0 || prevCost > 0) {
            rows.add(kpi("人力成本(实发)", String.format(Locale.ROOT, "%.0f", cost), changeRate(cost, prevCost)));
        }
        return rows;
    }

    private double sumCost(String fromS, String toS) {
        try {
            LocalDate from = LocalDate.parse(fromS);
            LocalDate to = LocalDate.parse(toS);
            return safeList(() -> analyticsMapper.listCostTrend(from.format(PERIOD), to.format(PERIOD)))
                    .stream()
                    .mapToDouble(r -> toDouble(r.get("netTotal")))
                    .sum();
        } catch (Exception e) {
            return 0;
        }
    }

    private static AnalyticsOverviewVO.KpiRow kpi(String metric, String value, Double rate) {
        String trend = "持平";
        if (rate != null) {
            if (rate > 0.0001) {
                trend = "上升";
            } else if (rate < -0.0001) {
                trend = "下降";
            }
        }
        return new AnalyticsOverviewVO.KpiRow(metric, value, rate, trend);
    }

    private static Double changeRate(Number current, Number previous) {
        if (current == null || previous == null) {
            return null;
        }
        double cur = current.doubleValue();
        double prev = previous.doubleValue();
        if (Math.abs(prev) < 1e-9) {
            return cur == 0 ? 0d : 1d;
        }
        return (cur - prev) / Math.abs(prev);
    }

    private static Double calcPunchRate(Map<String, Object> stats) {
        if (stats == null) {
            return null;
        }
        long punched = toLong(stats.get("punched"));
        long total = toLong(stats.get("total"));
        if (total <= 0) {
            return 0d;
        }
        return Math.min(1d, (double) punched / total);
    }

    private static Double calcApproveRate(Map<String, Object> stats) {
        if (stats == null) {
            return null;
        }
        long submitted = toLong(stats.get("submitted"));
        long approved = toLong(stats.get("approved"));
        if (submitted <= 0) {
            return 0d;
        }
        return (double) approved / submitted;
    }

    private static String formatPct(double rate) {
        return String.format(Locale.ROOT, "%.1f%%", rate * 100);
    }

    private static String labelProcess(String type) {
        if (!StringUtils.hasText(type)) {
            return "其他";
        }
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "ONBOARDING" -> "入职";
            case "REGULARIZATION" -> "转正";
            case "TRANSFER" -> "调岗";
            case "RESIGNATION" -> "离职";
            case "LEAVE" -> "请假";
            case "OVERTIME" -> "加班";
            case "MOBILE_CHANGE" -> "手机号变更";
            default -> type;
        };
    }

    private static Map<String, Long> toCountMap(List<Map<String, Object>> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            map.put(str(row.get("date")), toLong(row.get("cnt")));
        }
        return map;
    }

    private static LocalDate parseDay(String raw, LocalDate fallback) {
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private long safeLong(LongSupplier supplier) {
        try {
            Long v = supplier.get();
            return v == null ? 0L : v;
        } catch (Exception e) {
            log.warn("analytics kpi degraded: {}", e.getMessage());
            return 0L;
        }
    }

    private <T> List<T> safeList(ListSupplier<T> supplier) {
        try {
            List<T> list = supplier.get();
            return list != null ? list : List.of();
        } catch (Exception e) {
            log.warn("analytics list degraded: {}", e.getMessage());
            return List.of();
        }
    }

    private static long toLong(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString());
        } catch (Exception e) {
            return 0L;
        }
    }

    private static double toDouble(Object v) {
        if (v == null) {
            return 0d;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(v.toString());
        } catch (Exception e) {
            return 0d;
        }
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
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
