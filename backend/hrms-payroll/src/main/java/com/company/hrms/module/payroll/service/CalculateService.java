package com.company.hrms.module.payroll.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.hrms.attendance.entity.AttendanceMonthlySummary;
import com.company.hrms.attendance.mapper.AttendanceMonthlySummaryMapper;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.entity.EmployeeSalaryProfile;
import com.company.hrms.employee.mapper.EmployeeMapper;
import com.company.hrms.employee.mapper.EmployeeSalaryProfileMapper;
import com.company.hrms.module.org.entity.Department;
import com.company.hrms.module.org.mapper.DepartmentMapper;
import com.company.hrms.module.payroll.dto.*;
import com.company.hrms.payroll.entity.*;
import com.company.hrms.payroll.mapper.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalculateService {

    private final PayrollBatchMapper batchMapper;
    private final PayrollDetailMapper detailMapper;
    private final PayrollAdjustmentMapper adjustmentMapper;
    private final ObjectMapper objectMapper;

    // 员工模块 Mapper
    private final EmployeeMapper employeeMapper;
    private final EmployeeSalaryProfileMapper salaryProfileMapper;

    // 账套配置 Mapper
    private final PayrollSchemeMapper schemeMapper;
    private final PayrollSchemeItemMapper schemeItemMapper;
    private final PayrollSchemeScopeMapper schemeScopeMapper;

    // 个税 Mapper
    private final PayTaxBracketMapper taxBracketMapper;
    private final PayTaxYtdRecordMapper taxYtdRecordMapper;

    // 考勤 Mapper
    private final AttendanceMonthlySummaryMapper attendanceSummaryMapper;

    // 组织架构 Mapper（用于图表部门聚合）
    private final DepartmentMapper departmentMapper;

    // ==================== 薪资明细项 JSON 内部结构 ====================

    @Data
    private static class DetailItem {
        private String itemCode;
        private String itemName;
        private BigDecimal amount;
        private String type; // EARNING / DEDUCTION
        private List<PaySegment> segments; // 分段信息（仅 BASE_PAY 等分段项目有值）
    }

    /**
     * 分段计薪区间
     */
    @Data
    @AllArgsConstructor
    private static class PaySegment {
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal ratio; // 该段计薪比例（入职前=0，试用期=probationRatio，转正后=1）
        private String reason;    // 分段原因：入职前/试用期/转正后/全月在职
    }

    // ==================== 批次管理 ====================

    public PayrollBatch createBatch(String period, Long operatorId) {
        PayrollBatch exist = batchMapper.selectOne(new LambdaQueryWrapper<PayrollBatch>()
                .eq(PayrollBatch::getPeriod, period));
        if (exist != null) {
            throw new BusinessException(ErrorCode.PAYROLL_BATCH_EXISTS, "该账期批次已存在");
        }
        PayrollBatch batch = new PayrollBatch();
        batch.setPeriod(period);
        batch.setStatus("DRAFT");
        batch.setTotalCount(0);
        batch.setSuccessCount(0);
        batch.setGrossTotal(BigDecimal.ZERO);
        batch.setNetTotal(BigDecimal.ZERO);
        batch.setAnomalyCount(0);
        batch.setCreatedBy(operatorId);
        batchMapper.insert(batch);
        log.info("创建批次: id={}, period={}", batch.getId(), period);
        return batch;
    }

    public IPage<PayrollBatch> pageBatches(PageParam pageParam, String period) {
        LambdaQueryWrapper<PayrollBatch> wrapper = new LambdaQueryWrapper<PayrollBatch>()
                .orderByDesc(PayrollBatch::getCreatedAt);
        if (period != null) {
            wrapper.eq(PayrollBatch::getPeriod, period);
        }
        return batchMapper.selectPage(
                new Page<>(pageParam.getPage(), pageParam.getPageSize()),
                wrapper);
    }

    public PayrollBatch getBatch(Long id) {
        PayrollBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "批次不存在");
        }
        return batch;
    }

    // ==================== 核心核算方法 ====================

    /**
     * 执行批次核算：DRAFT → PENDING_CONFIRM
     * <p>核算流程：校验 → 加载在职员工 → 加载账套配置 → 逐员工计算 → 批量写入明细 → 更新批次统计</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void calculate(Long id) {
        // 1. 校验批次存在且状态为 DRAFT
        PayrollBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "批次不存在");
        }
        if (!"DRAFT".equals(batch.getStatus())) {
            throw new BusinessException(ErrorCode.PAYROLL_IN_PROGRESS, "仅草稿状态可开始计算");
        }

        // 2. 状态 → CALCULATING
        batch.setStatus("CALCULATING");
        batchMapper.updateById(batch);

        String period = batch.getPeriod();
        log.info("开始核算: batchId={}, period={}", id, period);

        // 清理该账期已有的个税YTD记录和核算明细（防止重算时唯一键冲突）
        taxYtdRecordMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.company.hrms.payroll.entity.PayTaxYtdRecord>()
                .eq(com.company.hrms.payroll.entity.PayTaxYtdRecord::getPeriod, period));
        detailMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.company.hrms.payroll.entity.PayrollDetail>()
                .eq(com.company.hrms.payroll.entity.PayrollDetail::getBatchId, id));

        try {
            // ========== 预加载配置数据 ==========

            // 3. 查询所有在职员工（试用期 10 + 正式 20）
            List<Employee> employees = employeeMapper.search(
                    null, null, null, List.of(10, 20), null, null, null, "");
            if (employees.isEmpty()) {
                log.warn("无在职员工，核算批次直接完成: batchId={}", id);
                finishBatch(batch);
                return;
            }

            // 4. 查询所有启用的账套
            List<PayrollScheme> schemes = schemeMapper.selectList(
                    new LambdaQueryWrapper<PayrollScheme>()
                            .eq(PayrollScheme::getStatus, "enabled")
                            .eq(PayrollScheme::getDeleted, 0));

            // 5. 批量加载账套的 Items 和 Scope
            Map<Long, List<PayrollSchemeItem>> schemeItemsMap = new HashMap<>();
            Map<Long, List<PayrollSchemeScope>> schemeScopeMap = new HashMap<>();
            for (PayrollScheme scheme : schemes) {
                schemeItemsMap.put(scheme.getId(), schemeItemMapper.selectBySchemeId(scheme.getId()));
                schemeScopeMap.put(scheme.getId(), schemeScopeMapper.selectBySchemeId(scheme.getId()));
            }

            // 6. 个税税率表
            int taxYear = Integer.parseInt(period.substring(0, 4));
            List<PayTaxBracket> taxBrackets = taxBracketMapper.selectByTaxYear(taxYear);

            // 7. 前序批次明细（用于环比异常检测）
            String prevPeriod = getPrevPeriod(period);
            PayrollBatch prevBatch = batchMapper.selectByPeriod(prevPeriod);
            Map<Long, PayrollDetail> prevDetailMap = new HashMap<>();
            if (prevBatch != null) {
                List<PayrollDetail> prevDetails = detailMapper.selectByBatchId(prevBatch.getId());
                for (PayrollDetail pd : prevDetails) {
                    prevDetailMap.put(pd.getEmployeeId(), pd);
                }
            }

            // ========== 逐员工核算 ==========

            List<PayrollDetail> details = new ArrayList<>();
            int successCount = 0;
            int anomalyCount = 0;
            BigDecimal grossTotal = BigDecimal.ZERO;
            BigDecimal netTotal = BigDecimal.ZERO;

            for (Employee employee : employees) {
                PayrollDetail detail = calculateForEmployee(
                        employee, period, id, schemes, schemeItemsMap, schemeScopeMap,
                        taxBrackets, prevDetailMap, prevPeriod, taxYear);

                details.add(detail);

                if ("SUCCESS".equals(detail.getCalcStatus())) {
                    successCount++;
                    grossTotal = grossTotal.add(detail.getGrossSalary());
                    netTotal = netTotal.add(detail.getNetSalary());
                }
                if (detail.getAnomalyFlags() != null && !"[]".equals(detail.getAnomalyFlags())
                        && !"".equals(detail.getAnomalyFlags())) {
                    anomalyCount++;
                }
            }

            // 8. 批量写入明细
            if (!details.isEmpty()) {
                detailMapper.batchInsert(details);
            }

            // 9. 更新批次统计
            batch.setTotalCount(employees.size());
            batch.setSuccessCount(successCount);
            batch.setAnomalyCount(anomalyCount);
            batch.setGrossTotal(grossTotal.setScale(2, RoundingMode.HALF_UP));
            batch.setNetTotal(netTotal.setScale(2, RoundingMode.HALF_UP));
            batch.setAttendanceLocked(1);
            batch.setStatus("PENDING_CONFIRM");
            batchMapper.updateById(batch);

            log.info("核算完成: batchId={}, period={}, total={}, success={}, anomaly={}, gross={}, net={}",
                    id, period, employees.size(), successCount, anomalyCount, grossTotal, netTotal);

        } catch (Exception e) {
            batch.setStatus("DRAFT");
            batchMapper.updateById(batch);
            log.error("核算失败: batchId={}", id, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "核算失败");
        }
    }

    /**
     * 无员工时的快速完成
     */
    private void finishBatch(PayrollBatch batch) {
        batch.setTotalCount(0);
        batch.setSuccessCount(0);
        batch.setAnomalyCount(0);
        batch.setGrossTotal(BigDecimal.ZERO);
        batch.setNetTotal(BigDecimal.ZERO);
        batch.setAttendanceLocked(1);
        batch.setStatus("PENDING_CONFIRM");
        batchMapper.updateById(batch);
    }

    // ==================== 单员工核算 ====================

    private PayrollDetail calculateForEmployee(
            Employee employee, String period, Long batchId,
            List<PayrollScheme> schemes,
            Map<Long, List<PayrollSchemeItem>> schemeItemsMap,
            Map<Long, List<PayrollSchemeScope>> schemeScopeMap,
            List<PayTaxBracket> taxBrackets,
            Map<Long, PayrollDetail> prevDetailMap,
            String prevPeriod, int taxYear) {

        // a. 查询薪资档案
        EmployeeSalaryProfile profile = salaryProfileMapper.selectByEmployeeId(employee.getId());

        // 异常：无薪资档案（阻断）
        if (profile == null) {
            return buildFailedDetail(batchId, employee.getId(), "NO_PROFILE", "员工无薪资档案");
        }

        // b. 匹配账套
        PayrollScheme matchedScheme = matchScheme(employee, profile, schemes, schemeScopeMap);
        if (matchedScheme == null) {
            return buildFailedDetail(batchId, employee.getId(), "NO_SCHEME", "无匹配账套");
        }

        // c. 获取账套工资项目
        List<PayrollSchemeItem> items = schemeItemsMap.get(matchedScheme.getId());
        if (items == null || items.isEmpty()) {
            return buildFailedDetail(batchId, employee.getId(), "NO_ITEMS", "账套无工资项目");
        }

        // d. 获取考勤数据（不可用时填 0）
        AttendanceMonthlySummary attendance = safeGetAttendance(employee.getId(), period);
        int lateCount = attendance != null && attendance.getLateCount() != null ? attendance.getLateCount() : 0;
        BigDecimal leaveDays = attendance != null && attendance.getLeaveDays() != null ? attendance.getLeaveDays() : BigDecimal.ZERO;
        BigDecimal overtimeHours = attendance != null && attendance.getOvertimeHours() != null ? attendance.getOvertimeHours() : BigDecimal.ZERO;

        // e. 按 sortOrder 排序后逐项计算
        List<PayrollSchemeItem> sortedItems = items.stream()
                .sorted(Comparator.comparing(PayrollSchemeItem::getSortOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        // 准备基础数据
        BigDecimal baseSalary = opt(profile.getBaseSalary());
        BigDecimal ssBase = opt(profile.getSsBase());
        BigDecimal hfBase = opt(profile.getHfBase());
        BigDecimal performanceBase = opt(profile.getPerformanceBase());
        BigDecimal probationRatio = opt(profile.getProbationRatio());
        if (probationRatio.compareTo(BigDecimal.ZERO) <= 0) {
            probationRatio = BigDecimal.ONE;
        }
        Map<String, BigDecimal> allowanceMap = parseAllowanceJson(profile.getAllowanceBaseJson());

        // 构建分段区间
        List<PaySegment> segments = buildSegments(period, employee.getHireDate(),
                employee.getProbationEndDate(), probationRatio);

        List<DetailItem> detailItems = new ArrayList<>();
        boolean hasTaxItem = false;

        for (PayrollSchemeItem item : sortedItems) {
            if ("TAX".equals(item.getItemType())) {
                hasTaxItem = true;
                continue; // 个税单独计算
            }
            DetailItem di = calcItem(item, baseSalary, ssBase, hfBase, performanceBase,
                    allowanceMap, lateCount, leaveDays, overtimeHours, segments, period);
            detailItems.add(di);
        }

        // f. 累计预扣法计算个税
        DetailItem taxItem = calcTax(detailItems, employee.getId(), period,
                baseSalary, ssBase, hfBase, taxBrackets, taxYear);
        if (!hasTaxItem) {
            // 如果账套没有配置 TAX 项，仍然计算并附加
            detailItems.add(taxItem);
        } else {
            detailItems.add(taxItem);
        }

        // g. 汇总应发 / 实发
        BigDecimal grossSalary = BigDecimal.ZERO;
        BigDecimal deductions = BigDecimal.ZERO;
        for (DetailItem di : detailItems) {
            if ("EARNING".equals(di.getType())) {
                grossSalary = grossSalary.add(opt(di.getAmount()));
            } else {
                deductions = deductions.add(opt(di.getAmount()).abs());
            }
        }
        BigDecimal netSalary = grossSalary.subtract(deductions);
        if (netSalary.compareTo(BigDecimal.ZERO) < 0) {
            netSalary = BigDecimal.ZERO;
        }

        // h. 异常检测
        List<String> anomalyFlags = new ArrayList<>();
        // LEAVE_HIGH: 请假 > 15 天
        if (leaveDays.compareTo(new BigDecimal("15")) > 0) {
            anomalyFlags.add("LEAVE_HIGH");
        }
        // OVERTIME_HIGH: 加班 > 50 小时
        if (overtimeHours.compareTo(new BigDecimal("50")) > 0) {
            anomalyFlags.add("OVERTIME_HIGH");
        }
        // SALARY_CHANGE_HIGH: 环比波动 > 30%
        BigDecimal prevNetSalary = BigDecimal.ZERO;
        PayrollDetail prevDetail = prevDetailMap.get(employee.getId());
        if (prevDetail != null && prevDetail.getNetSalary() != null
                && prevDetail.getNetSalary().compareTo(BigDecimal.ZERO) > 0) {
            prevNetSalary = prevDetail.getNetSalary();
            BigDecimal diff = netSalary.subtract(prevNetSalary).abs();
            BigDecimal ratio = diff.divide(prevNetSalary, 4, RoundingMode.HALF_UP);
            if (ratio.compareTo(new BigDecimal("0.3")) > 0) {
                anomalyFlags.add("SALARY_CHANGE_HIGH");
            }
        }

        // i. 构建 PayrollDetail
        PayrollDetail detail = new PayrollDetail();
        detail.setBatchId(batchId);
        detail.setEmployeeId(employee.getId());
        detail.setCalcStatus("SUCCESS");
        detail.setGrossSalary(grossSalary.setScale(2, RoundingMode.HALF_UP));
        detail.setNetSalary(netSalary.setScale(2, RoundingMode.HALF_UP));
        detail.setPrevNetSalary(prevNetSalary.setScale(2, RoundingMode.HALF_UP));
        detail.setManualAdjusted(0);
        try {
            detail.setDetailJson(objectMapper.writeValueAsString(detailItems));
            detail.setAnomalyFlags(objectMapper.writeValueAsString(anomalyFlags));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("明细 JSON 序列化失败", e);
        }

        return detail;
    }

    // ==================== 分段计薪 ====================

    /**
     * 根据员工入职日期、转正日期构建分段区间
     * <p>支持：全月在职(1段)、月中入职(2段)、月中转正(2段)、入职+转正同月(3段)</p>
     *
     * @param period          账期 如 "2026-07"
     * @param hireDate        入职日期
     * @param probationEndDate 转正日期
     * @param probationRatio  试用期待遇比例
     * @return 分段列表
     */
    private List<PaySegment> buildSegments(String period, LocalDate hireDate,
                                            LocalDate probationEndDate, BigDecimal probationRatio) {
        YearMonth ym = YearMonth.parse(period);
        LocalDate periodStart = ym.atDay(1);
        LocalDate periodEnd = ym.atEndOfMonth();

        // 无入职日期 → 全月在职 1 段
        if (hireDate == null) {
            return List.of(new PaySegment(periodStart, periodEnd, BigDecimal.ONE, "全月在职"));
        }

        // 收集时间切分点（每个切分点表示一段的起始）
        Set<LocalDate> points = new TreeSet<>();
        points.add(periodStart);
        points.add(periodEnd.plusDays(1)); // 末段终点（独占）

        // 入职日作为切分点（只在入职日在账期内时）
        if (!hireDate.isBefore(periodStart) && !hireDate.isAfter(periodEnd)) {
            points.add(hireDate);
        }

        // 转正次日作为切分点（只在转正日在账期内时）
        if (probationEndDate != null
                && !probationEndDate.isBefore(periodStart)
                && probationEndDate.isBefore(periodEnd)) {
            points.add(probationEndDate.plusDays(1));
        }

        // 生成分段
        List<LocalDate> sortedPoints = new ArrayList<>(points);
        List<PaySegment> segments = new ArrayList<>();

        for (int i = 0; i < sortedPoints.size() - 1; i++) {
            LocalDate segStart = sortedPoints.get(i);
            LocalDate segEnd = sortedPoints.get(i + 1).minusDays(1);

            BigDecimal ratio;
            String reason;

            if (segEnd.isBefore(hireDate)) {
                // 入职前：不计薪
                ratio = BigDecimal.ZERO;
                reason = "入职前";
            } else if (probationEndDate != null && !segEnd.isAfter(probationEndDate)) {
                // 试用期
                ratio = probationRatio;
                reason = "试用期";
            } else {
                // 转正后 / 入职后无试用
                ratio = BigDecimal.ONE;
                if (hireDate != null && segStart.equals(hireDate)) {
                    reason = "入职后";
                } else if (probationEndDate != null
                        && segStart.equals(probationEndDate.plusDays(1))) {
                    reason = "转正后";
                } else {
                    reason = "全月在职";
                }
            }

            segments.add(new PaySegment(segStart, segEnd, ratio, reason));
        }

        return segments;
    }

    /**
     * 基于分段计算基本工资
     */
    private BigDecimal calcBasePayWithSegments(BigDecimal baseSalary,
                                                List<PaySegment> segments, String period) {
        if (segments == null || segments.isEmpty()) {
            return baseSalary;
        }

        YearMonth ym = YearMonth.parse(period);
        long totalDays = ym.lengthOfMonth();

        // 全月在职且比例=1 → 直接返回 baseSalary
        if (segments.size() == 1) {
            PaySegment seg = segments.get(0);
            long segDays = ChronoUnit.DAYS.between(seg.getStartDate(), seg.getEndDate()) + 1;
            if (segDays == totalDays && seg.getRatio().compareTo(BigDecimal.ONE) >= 0) {
                return baseSalary;
            }
            if (seg.getRatio().compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
        }

        // 按日历天数比例计算
        BigDecimal dailySalary = baseSalary.divide(BigDecimal.valueOf(totalDays),
                10, RoundingMode.HALF_UP);
        BigDecimal total = BigDecimal.ZERO;
        for (PaySegment seg : segments) {
            long segDays = ChronoUnit.DAYS.between(seg.getStartDate(), seg.getEndDate()) + 1;
            total = total.add(dailySalary
                    .multiply(BigDecimal.valueOf(segDays))
                    .multiply(seg.getRatio()));
        }
        return total;
    }

    // ==================== 薪资项目计算 ====================

    private DetailItem calcItem(PayrollSchemeItem item,
                                BigDecimal baseSalary, BigDecimal ssBase, BigDecimal hfBase,
                                BigDecimal performanceBase, Map<String, BigDecimal> allowanceMap,
                                int lateCount, BigDecimal leaveDays, BigDecimal overtimeHours,
                                List<PaySegment> segments, String period) {

        BigDecimal amount = BigDecimal.ZERO;
        String type = "EARNING";

        switch (item.getItemType()) {
            case "FIXED": {
                if ("BASE_PAY".equals(item.getItemCode())) {
                    amount = calcBasePayWithSegments(baseSalary, segments, period);
                } else if ("POSITION_ALLOWANCE".equals(item.getItemCode())) {
                    // 从 allowanceBaseJson 解析
                    amount = allowanceMap.getOrDefault(item.getItemCode(),
                            allowanceMap.getOrDefault("POSITION_ALLOWANCE", BigDecimal.ZERO));
                } else {
                    // 其他固定项目尝试从津贴 JSON 获取
                    amount = allowanceMap.getOrDefault(item.getItemCode(), BigDecimal.ZERO);
                }
                break;
            }
            case "VARIABLE": {
                if ("PERFORMANCE_BONUS".equals(item.getItemCode())) {
                    BigDecimal ratio = opt(item.getRatio());
                    if (ratio.compareTo(BigDecimal.ZERO) <= 0) ratio = BigDecimal.ONE;
                    amount = performanceBase.multiply(ratio);
                } else if ("OVERTIME_PAY".equals(item.getItemCode())) {
                    // (baseSalary / 21.75 / 8) * 1.5 * overtimeHours
                    BigDecimal dailyRate = baseSalary.divide(new BigDecimal("21.75"), 10, RoundingMode.HALF_UP);
                    BigDecimal hourlyRate = dailyRate.divide(new BigDecimal("8"), 10, RoundingMode.HALF_UP);
                    amount = hourlyRate.multiply(new BigDecimal("1.5")).multiply(overtimeHours);
                } else {
                    // 通用变动项目：performanceBase × ratio
                    BigDecimal ratio = opt(item.getRatio());
                    amount = performanceBase.multiply(ratio);
                }
                break;
            }
            case "ATTENDANCE_DEDUCT": {
                type = "DEDUCTION";
                if ("LATE_DEDUCT".equals(item.getItemCode())) {
                    amount = new BigDecimal("-50").multiply(BigDecimal.valueOf(lateCount));
                } else if ("LEAVE_DEDUCT".equals(item.getItemCode())) {
                    // -(baseSalary / 21.75) * leaveDays
                    BigDecimal dailyRate = baseSalary.divide(new BigDecimal("21.75"), 10, RoundingMode.HALF_UP);
                    amount = dailyRate.multiply(leaveDays).negate();
                } else {
                    amount = BigDecimal.ZERO;
                }
                break;
            }
            case "SS_DEDUCT": {
                type = "DEDUCTION";
                // -ssBase × ratio
                BigDecimal ratio = opt(item.getRatio());
                amount = ssBase.multiply(ratio).negate();
                break;
            }
            case "HF_DEDUCT": {
                type = "DEDUCTION";
                // -hfBase × ratio
                BigDecimal ratio = opt(item.getRatio());
                amount = hfBase.multiply(ratio).negate();
                break;
            }
            default:
                amount = BigDecimal.ZERO;
        }

        DetailItem di = new DetailItem();
        di.setItemCode(item.getItemCode());
        di.setItemName(item.getItemName());
        di.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        di.setType(type);
        // 基本工资项目记录分段快照
        if ("BASE_PAY".equals(item.getItemCode())) {
            di.setSegments(segments);
        }
        return di;
    }

    // ==================== 累计预扣法个税 ====================

    private DetailItem calcTax(List<DetailItem> detailItems, Long employeeId, String period,
                               BigDecimal baseSalary, BigDecimal ssBase, BigDecimal hfBase,
                               List<PayTaxBracket> taxBrackets, int taxYear) {

        // 本期收入合计（所有 EARNING 类型）
        BigDecimal currentGross = detailItems.stream()
                .filter(d -> "EARNING".equals(d.getType()))
                .map(d -> opt(d.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 本期社保 + 公积金（取绝对值，正数）
        BigDecimal currentSsHf = detailItems.stream()
                .filter(d -> "SS_DEDUCT".equals(d.getType()) || "HF_DEDUCT".equals(d.getType()))
                .map(d -> opt(d.getAmount()).abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 上月累计预扣数据
        String prevPeriod = getPrevPeriod(period);
        PayTaxYtdRecord prevYtd = taxYtdRecordMapper.selectByEmployeeAndPeriod(employeeId, prevPeriod);

        BigDecimal prevCumulativeTax = BigDecimal.ZERO;

        // 当月月份数（从 period 解析，如 "2026-07" -> 7）
        int monthNum = Integer.parseInt(period.substring(5));
        BigDecimal taxFreeBase = new BigDecimal("5000").multiply(BigDecimal.valueOf(monthNum));

        BigDecimal cumulativeTaxable;

        if (prevYtd != null && prevYtd.getTaxableIncome() != null) {
            // 累计预扣应纳税所得额 = 上月累计 + 本月收入 - 5000 - 本月社保公积金
            cumulativeTaxable = prevYtd.getTaxableIncome()
                    .add(currentGross)
                    .subtract(new BigDecimal("5000"))
                    .subtract(currentSsHf);
            prevCumulativeTax = opt(prevYtd.getCumulativeTax());
        } else {
            // 本年为首次核算
            // 累计预扣应纳税所得额 = 累计收入 - 5000×月数 - 累计社保公积金
            cumulativeTaxable = currentGross
                    .subtract(taxFreeBase)
                    .subtract(currentSsHf);
        }

        // 应纳税所得额 <= 0，无需缴税
        BigDecimal cumulativeTax = BigDecimal.ZERO;
        BigDecimal thisPeriodTax = BigDecimal.ZERO;

        if (cumulativeTaxable.compareTo(BigDecimal.ZERO) > 0) {
            PayTaxBracket bracket = findTaxBracket(cumulativeTaxable, taxBrackets);
            if (bracket != null) {
                cumulativeTax = cumulativeTaxable.multiply(bracket.getRate())
                        .subtract(bracket.getQuickDeduction());
                if (cumulativeTax.compareTo(BigDecimal.ZERO) < 0) {
                    cumulativeTax = BigDecimal.ZERO;
                }
            }
            // 本期预扣 = 累计应预扣 - 已累计预扣
            thisPeriodTax = cumulativeTax.subtract(prevCumulativeTax);
            if (thisPeriodTax.compareTo(BigDecimal.ZERO) < 0) {
                thisPeriodTax = BigDecimal.ZERO;
            }
        }

        // 保存本期 YTD 记录供下期使用
        PayTaxYtdRecord ytdRecord = new PayTaxYtdRecord();
        ytdRecord.setEmployeeId(employeeId);
        ytdRecord.setPeriod(period);
        ytdRecord.setTaxableIncome(cumulativeTaxable.setScale(2, RoundingMode.HALF_UP));
        ytdRecord.setTaxDeducted(thisPeriodTax.setScale(2, RoundingMode.HALF_UP));
        ytdRecord.setCumulativeTax(cumulativeTax.setScale(2, RoundingMode.HALF_UP));
        taxYtdRecordMapper.insert(ytdRecord);

        DetailItem di = new DetailItem();
        di.setItemCode("TAX");
        di.setItemName("个人所得税");
        di.setAmount(thisPeriodTax.negate().setScale(2, RoundingMode.HALF_UP)); // 扣款为负
        di.setType("DEDUCTION");
        return di;
    }

    // ==================== 个税税率查找 ====================

    private PayTaxBracket findTaxBracket(BigDecimal cumulativeTaxable, List<PayTaxBracket> brackets) {
        if (brackets == null || brackets.isEmpty()) return null;
        for (PayTaxBracket bracket : brackets) {
            if (bracket.getMaxTaxable() == null) {
                // 最高档（上不封顶）
                if (cumulativeTaxable.compareTo(bracket.getMinTaxable()) >= 0) {
                    return bracket;
                }
            } else {
                if (cumulativeTaxable.compareTo(bracket.getMinTaxable()) >= 0
                        && cumulativeTaxable.compareTo(bracket.getMaxTaxable()) < 0) {
                    return bracket;
                }
            }
        }
        // 如果没找到（小于最低档），返回最低档
        return brackets.isEmpty() ? null : brackets.get(0);
    }

    // ==================== 账套匹配 ====================

    private PayrollScheme matchScheme(Employee employee, EmployeeSalaryProfile profile,
                                      List<PayrollScheme> schemes,
                                      Map<Long, List<PayrollSchemeScope>> schemeScopeMap) {
        // 优先使用档案中指定的账套
        if (profile.getSchemeId() != null) {
            for (PayrollScheme scheme : schemes) {
                if (scheme.getId().equals(profile.getSchemeId())) {
                    return scheme;
                }
            }
        }
        // 其次按 Scope 匹配
        for (PayrollScheme scheme : schemes) {
            List<PayrollSchemeScope> scopes = schemeScopeMap.get(scheme.getId());
            if (scopes == null || scopes.isEmpty()) {
                // 无范围限制的账套适用于所有人
                return scheme;
            }
            if (matchesAnyScope(employee, scopes)) {
                return scheme;
            }
        }
        // 最后兜底：取第一个启用的账套
        if (!schemes.isEmpty()) {
            return schemes.get(0);
        }
        return null;
    }

    private boolean matchesAnyScope(Employee employee, List<PayrollSchemeScope> scopes) {
        for (PayrollSchemeScope scope : scopes) {
            if (employee == null) continue;
            switch (scope.getScopeType()) {
                case "DEPARTMENT":
                    if (employee.getDepartmentId() != null
                            && String.valueOf(employee.getDepartmentId()).equals(scope.getScopeId())) {
                        return true;
                    }
                    break;
                case "POSITION":
                    if (employee.getPositionId() != null
                            && String.valueOf(employee.getPositionId()).equals(scope.getScopeId())) {
                        return true;
                    }
                    break;
                case "JOB_LEVEL":
                    if (employee.getGrade() != null
                            && employee.getGrade().equals(scope.getScopeId())) {
                        return true;
                    }
                    break;
                default:
                    // 未知范围类型，跳过
            }
        }
        return false;
    }

    // ==================== 考勤安全查询 ====================

    private AttendanceMonthlySummary safeGetAttendance(Long employeeId, String period) {
        try {
            return attendanceSummaryMapper.selectByEmployeeAndPeriod(employeeId, period);
        } catch (Exception e) {
            log.warn("考勤数据查询失败，使用默认值 0: employeeId={}, period={}, error={}",
                    employeeId, period, e.getMessage());
            return null;
        }
    }

    // ==================== 异常明细构造 ====================

    private PayrollDetail buildFailedDetail(Long batchId, Long employeeId, String flag, String reason) {
        PayrollDetail detail = new PayrollDetail();
        detail.setBatchId(batchId);
        detail.setEmployeeId(employeeId);
        detail.setCalcStatus("FAILED");
        detail.setGrossSalary(BigDecimal.ZERO);
        detail.setNetSalary(BigDecimal.ZERO);
        detail.setPrevNetSalary(BigDecimal.ZERO);
        detail.setManualAdjusted(0);
        detail.setDetailJson("[]");
        try {
            detail.setAnomalyFlags(objectMapper.writeValueAsString(List.of(flag)));
        } catch (JsonProcessingException e) {
            detail.setAnomalyFlags("[\"" + flag + "\"]");
        }
        return detail;
    }

    // ==================== 辅助工具方法 ====================

    /** 获取前序账期：2026-07 → 2026-06，2026-01 → 2025-12 */
    private String getPrevPeriod(String period) {
        int year = Integer.parseInt(period.substring(0, 4));
        int month = Integer.parseInt(period.substring(5));
        if (month == 1) {
            return (year - 1) + "-12";
        }
        return String.format("%04d-%02d", year, month - 1);
    }

    /** 安全获取 BigDecimal，null 时返回 ZERO */
    private BigDecimal opt(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }

    /** 解析津贴 JSON: {"POSITION_ALLOWANCE": 2000.00, ...} */
    private Map<String, BigDecimal> parseAllowanceJson(String json) {
        Map<String, BigDecimal> result = new HashMap<>();
        if (json == null || json.isBlank()) return result;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = objectMapper.readValue(json, Map.class);
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (entry.getValue() instanceof Number) {
                    result.put(entry.getKey(), BigDecimal.valueOf(((Number) entry.getValue()).doubleValue()));
                }
            }
        } catch (Exception e) {
            log.warn("allowanceBaseJson 解析失败, json={}", json, e);
        }
        return result;
    }

    // ==================== 核算明细查询 ====================

    public PageResult<PayrollDetailVO> getDetails(Long batchId, PageParam pageParam) {
        LambdaQueryWrapper<PayrollDetail> wrapper = new LambdaQueryWrapper<PayrollDetail>()
                .eq(PayrollDetail::getBatchId, batchId);
        IPage<PayrollDetail> page = detailMapper.selectPage(
                new Page<>(pageParam.getPage(), pageParam.getPageSize()),
                wrapper);

        // 收集员工 ID 批量查询姓名
        Set<Long> empIds = page.getRecords().stream()
                .map(PayrollDetail::getEmployeeId)
                .collect(Collectors.toSet());
        Map<Long, String> nameMap = new HashMap<>();
        for (Long empId : empIds) {
            try {
                Employee emp = employeeMapper.selectById(empId);
                if (emp != null) nameMap.put(empId, emp.getName());
            } catch (Exception e) {
                log.warn("查询员工姓名失败: employeeId={}", empId);
            }
        }

        List<PayrollDetailVO> voList = page.getRecords().stream().map(d -> {
            PayrollDetailVO vo = new PayrollDetailVO();
            vo.setEmployeeId(d.getEmployeeId());
            vo.setEmployeeName(nameMap.getOrDefault(d.getEmployeeId(), String.valueOf(d.getEmployeeId())));
            vo.setGrossSalary(d.getGrossSalary());
            vo.setNetSalary(d.getNetSalary());
            vo.setCalcStatus(d.getCalcStatus());
            vo.setManualAdjusted(d.getManualAdjusted() == 1);
            if (d.getAnomalyFlags() != null) {
                try {
                    vo.setAnomalyFlags(objectMapper.readValue(d.getAnomalyFlags(), List.class));
                } catch (Exception e) {
                    vo.setAnomalyFlags(Collections.emptyList());
                }
            } else {
                vo.setAnomalyFlags(Collections.emptyList());
            }
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(voList, page.getTotal(), pageParam);
    }

    // ==================== 图表数据聚合 ====================

    public ChartDataVO getChartData(Long batchId) {
        ChartDataVO vo = new ChartDataVO();

        // 查询批次
        PayrollBatch batch = batchMapper.selectById(batchId);
        if (batch == null) return vo;

        // 查询该批次所有明细
        List<PayrollDetail> details = detailMapper.selectByBatchId(batchId);
        if (details.isEmpty()) return vo;

        // ------- costTrend: 当前批次统计 -------
        ChartDataVO.CostTrendItem trendItem = new ChartDataVO.CostTrendItem();
        trendItem.setPeriod(batch.getPeriod());
        double totalGross = details.stream()
                .filter(d -> d.getGrossSalary() != null)
                .mapToDouble(d -> d.getGrossSalary().doubleValue())
                .sum();
        trendItem.setGrossTotal(totalGross);
        vo.getCostTrend().add(trendItem);

        // ------- deptDistribution: 按部门聚合 -------
        // 收集所有员工部门信息
        Set<Long> empIds = details.stream()
                .map(PayrollDetail::getEmployeeId)
                .collect(Collectors.toSet());

        Map<Long, Long> empDeptMap = new HashMap<>(); // employeeId -> departmentId
        for (Long eid : empIds) {
            try {
                Employee emp = employeeMapper.selectById(eid);
                if (emp != null && emp.getDepartmentId() != null) {
                    empDeptMap.put(eid, emp.getDepartmentId());
                }
            } catch (Exception e) {
                log.warn("图表: 查询员工部门失败, employeeId={}", eid);
            }
        }

        // 按部门聚合 grossTotal
        Map<Long, Double> deptGrossMap = new HashMap<>();
        for (PayrollDetail d : details) {
            Long deptId = empDeptMap.get(d.getEmployeeId());
            if (deptId != null && d.getGrossSalary() != null) {
                deptGrossMap.merge(deptId, d.getGrossSalary().doubleValue(), Double::sum);
            }
        }

        // 查询部门名称
        for (Map.Entry<Long, Double> entry : deptGrossMap.entrySet()) {
            String deptName = "部门" + entry.getKey();
            try {
                Department dept = departmentMapper.selectById(entry.getKey());
                if (dept != null) {
                    deptName = dept.getName();
                }
            } catch (Exception e) {
                log.warn("图表: 查询部门名称失败, deptId={}", entry.getKey());
            }
            ChartDataVO.DeptDistItem deptItem = new ChartDataVO.DeptDistItem();
            deptItem.setDeptName(deptName);
            deptItem.setGrossTotal(entry.getValue());
            vo.getDeptDistribution().add(deptItem);
        }

        return vo;
    }

    // ==================== 手工调整 ====================

    @Transactional(rollbackFor = Exception.class)
    public void adjust(Long batchId, Long detailId, AdjustmentDTO dto, Long operatorId) {
        PayrollDetail detail = detailMapper.selectById(detailId);
        if (detail == null || !detail.getBatchId().equals(batchId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "核算明细不存在");
        }
        PayrollAdjustment adj = new PayrollAdjustment();
        adj.setDetailId(detailId);
        adj.setItemCode(dto.getItemCode());
        adj.setAdjustAmount(BigDecimal.valueOf(dto.getAdjustAmount()));
        adj.setReason(dto.getReason());
        adj.setOperatorId(operatorId);
        adjustmentMapper.insert(adj);

        detail.setManualAdjusted(1);
        detailMapper.updateById(detail);
        log.info("手工调整: batchId={}, detailId={}, amount={}", batchId, detailId, dto.getAdjustAmount());
    }

    // ==================== 提交审批 ====================

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        PayrollBatch batch = batchMapper.selectById(id);
        if (batch == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "批次不存在");
        if (!"PENDING_CONFIRM".equals(batch.getStatus())) {
            throw new BusinessException(ErrorCode.PAYROLL_IN_PROGRESS, "仅待确认状态可提交审批");
        }
        batch.setStatus("APPROVING");
        batchMapper.updateById(batch);
        log.info("提交审批: batchId={}", id);
    }

    // ==================== 发放确认 ====================

    @Transactional(rollbackFor = Exception.class)
    public void distribute(Long id) {
        PayrollBatch batch = batchMapper.selectById(id);
        if (batch == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "批次不存在");
        if (!"APPROVED".equals(batch.getStatus())) {
            throw new BusinessException(ErrorCode.PAYROLL_IN_PROGRESS, "仅已通过状态可发放");
        }
        batch.setStatus("DISTRIBUTED");
        batchMapper.updateById(batch);
        log.info("发放确认: batchId={}", id);
    }
}
