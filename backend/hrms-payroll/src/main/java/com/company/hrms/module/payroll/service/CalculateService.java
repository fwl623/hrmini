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

/**
 * 薪资核算 Service（核心）
 *
 * 负责每月薪资的批量核算，涵盖：
 * 1. 批次管理（创建、查询、状态流转）
 * 2. 逐员工薪资计算（账套匹配 → 分段计薪 → 逐项计算 → 个税 → 异常检测）
 * 3. 累计预扣法个税计算（跨月 YTD 累计）
 * 4. 手工调整、图表聚合、成本报表
 *
 * 批次状态机：
 *   DRAFT → CALCULATING → PENDING_CONFIRM → APPROVING → APPROVED → DISTRIBUTED
 *                           ↑                                  │
 *                           └────────── REJECTED ←─────────────┘
 *
 * 与考勤模块联动：读取 AttendanceMonthlySummary 用于迟到/请假扣款
 * 与加班模块联动：读取 OvertimeLedger 按倍率分组计算加班费
 */
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
    private final com.company.hrms.attendance.mapper.OvertimeLedgerMapper overtimeLedgerMapper;
    private final com.company.hrms.attendance.mapper.AttendanceDailySummaryMapper attendanceDailySummaryMapper;
    private final com.company.hrms.attendance.mapper.AttendanceMonthLockMapper attendanceMonthLockMapper;

    // 组织架构 Mapper（用于图表部门聚合）
    private final DepartmentMapper departmentMapper;

    // ========================================================================
    //  内部数据结构：薪资明细项、分段区间
    // ========================================================================

    /** 薪资明细项（序列化为 detail_json 存储） */
    @Data
    private static class DetailItem {
        private String itemCode;
        private String itemName;
        private BigDecimal amount;
        private String type; // EARNING(应发项) / DEDUCTION(扣款项)
        private List<PaySegment> segments; // 分段信息（仅 BASE_PAY 有值）
    }

    /**
     * 分段计薪区间
     *
     * 支持月中入职、转正等场景的比例分段。
     * 例如月中入职：分段为 [入职前=0] + [入职后=1]
     * 月中转正：分段为 [试用期=probationRatio] + [转正后=1]
     */
    @Data
    @AllArgsConstructor
    private static class PaySegment {
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal ratio; // 该段计薪比例（入职前=0，试用期=probationRatio，转正后=1）
        private String reason;    // 分段原因：入职前/试用期/转正后/全月在职
    }

    // ========================================================================
    //  批次管理
    // ========================================================================

    /**
     * 创建核算批次
     *
     * 一个账期只允许创建一个批次（唯一约束 uk_period）。
     * 初始状态为 DRAFT。
     *
     * @param period     账期（格式 YYYY-MM）
     * @param operatorId 操作人（HR）ID
     * @return 创建的批次
     */
    public PayrollBatch createBatch(String period, Long operatorId) {
        // 1. 校验账期不能是未来月份
        YearMonth currentMonth = YearMonth.now();
        YearMonth targetMonth = YearMonth.parse(period);
        if (targetMonth.isAfter(currentMonth)) {
            throw new BusinessException(ErrorCode.PAYROLL_FUTURE_PERIOD, "不允许创建未来月份的核算批次");
        }

        // 2. 校验批次是否已存在
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

    /** 批次列表分页查询 */
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

    /** 批次详情查询 */
    public PayrollBatch getBatch(Long id) {
        PayrollBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "批次不存在");
        }
        return batch;
    }

    /** 核算前置校验（异步前同步执行） */
    public void validateBeforeCalculate(PayrollBatch batch) {
        String period = batch.getPeriod();
        YearMonth targetMonth = YearMonth.parse(period);
        YearMonth currentMonth = YearMonth.now();

        if (targetMonth.isAfter(currentMonth)) {
            throw new BusinessException(ErrorCode.PAYROLL_FUTURE_PERIOD, "未来月份的批次不允许计算");
        }
        if (targetMonth.equals(currentMonth)) {
            com.company.hrms.attendance.entity.AttendanceMonthLock lock =
                    attendanceMonthLockMapper.selectOne(
                            new LambdaQueryWrapper<com.company.hrms.attendance.entity.AttendanceMonthLock>()
                                    .eq(com.company.hrms.attendance.entity.AttendanceMonthLock::getYearMonth, period));
            if (lock == null || lock.getStatus() != 20) {
                throw new BusinessException(ErrorCode.ATTENDANCE_NOT_LOCKED,
                        "当前月考勤数据未锁定，请先完成考勤月结");
            }
        }
    }

    /** 更新批次状态 */
    public void updateBatchStatus(PayrollBatch batch) {
        batchMapper.updateById(batch);
    }

    // ========================================================================
    //  核心核算
    // ========================================================================

    /**
     * 执行批次核算（DRAFT → PENDING_CONFIRM）
     *
     * 完整流程：
     *   1. 校验批次状态为 DRAFT
     *   2. 状态 → CALCULATING
     *   3. 清理该账期旧明细和个税 YTD（防重算冲突）
     *   4. 查询所有在职员工（试用期 + 正式）
     *   5. 加载所有启用的账套及 Items/Scope 配置
     *   6. 加载个税税率表、上月环比数据
     *   7. 逐员工核算（calculateForEmployee）
     *   8. 批量写入明细 → 更新批次统计 → PENDING_CONFIRM
     *
     * 异常时回滚明细并恢复状态为 DRAFT（保留原数据）。
     *
     * @param id 批次 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void calculate(Long id) {
        // 1. 校验批次存在且状态为 CALCULATING（异步消费时状态已由 Controller 更新）
        PayrollBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "批次不存在");
        }
        if (!"CALCULATING".equals(batch.getStatus())) {
            throw new BusinessException(ErrorCode.PAYROLL_IN_PROGRESS, "批次状态异常，需为计算中");
        }

        String period = batch.getPeriod();
        log.info("开始核算: batchId={}, period={}", id, period);

        // 清理该账期已有的个税YTD记录和核算明细（防止重算时唯一键冲突）
        taxYtdRecordMapper.delete(new LambdaQueryWrapper<PayTaxYtdRecord>()
                .eq(PayTaxYtdRecord::getPeriod, period));
        detailMapper.delete(new LambdaQueryWrapper<PayrollDetail>()
                .eq(PayrollDetail::getBatchId, id));

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

            // 5. 批量加载账套的 Items 和 Scope（避免 N+1）
            Map<Long, List<PayrollSchemeItem>> schemeItemsMap = new HashMap<>();
            Map<Long, List<PayrollSchemeScope>> schemeScopeMap = new HashMap<>();
            for (PayrollScheme scheme : schemes) {
                schemeItemsMap.put(scheme.getId(), schemeItemMapper.selectBySchemeId(scheme.getId()));
                schemeScopeMap.put(scheme.getId(), schemeScopeMapper.selectBySchemeId(scheme.getId()));
            }

            // 6. 个税税率表（查当年）
            int taxYear = Integer.parseInt(period.substring(0, 4));
            List<PayTaxBracket> taxBrackets = taxBracketMapper.selectByTaxYear(taxYear);

            // 7. 前序批次明细（用于环比异常检测 SALARY_CHANGE_HIGH）
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
            // 核算失败时恢复 DRAFT 状态（事务回滚已撤销明细）
            batch.setStatus("DRAFT");
            batchMapper.updateById(batch);
            log.error("核算失败: batchId={}", id, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "核算失败");
        }
    }

    /** 无员工时的快速完成（清空批次统计） */
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

    // ========================================================================
    //  单员工核算
    // ========================================================================

    /**
     * 单员工薪资核算
     *
     * 流程：
     *   a. 查询薪资档案（EmployeeSalaryProfile），无档案→FAILED
     *   b. 匹配账套（优先档案指定 → Scope匹配 → 兜底第一个启用账套）
     *   c. 获取工资项目列表（按 sortOrder 排序）
     *   d. 获取月考勤数据（迟到次数、请假天数、加班时长）
     *   e. 构建分段区间（buildSegments），支持入职/转正分段
     *   f. 逐项计算（calcItem），按 item_type 分发
     *   g. 累计预扣法计算个税（calcTax）
     *   h. 汇总应发/实发
     *   i. 异常检测（请假>15天、加班>50h、环比波动>30%）
     */
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

        // d. 获取考勤数据（不可用时填 0，不影响核算）
        AttendanceMonthlySummary attendance = safeGetAttendance(employee.getId(), period);
        int lateCount = attendance != null && attendance.getLateCount() != null ? attendance.getLateCount() : 0;
        BigDecimal leaveDays = attendance != null && attendance.getLeaveDays() != null ? attendance.getLeaveDays() : BigDecimal.ZERO;
        BigDecimal overtimeHours = attendance != null && attendance.getOvertimeHours() != null ? attendance.getOvertimeHours() : BigDecimal.ZERO;

        // e. 按 sortOrder 排序后逐项计算
        List<PayrollSchemeItem> sortedItems = items.stream()
                .sorted(Comparator.comparing(PayrollSchemeItem::getSortOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        // 准备基础数据：薪资档案中的各项基数
        BigDecimal baseSalary = opt(profile.getBaseSalary());
        BigDecimal ssBase = opt(profile.getSsBase());
        BigDecimal hfBase = opt(profile.getHfBase());
        BigDecimal performanceBase = opt(profile.getPerformanceBase());
        BigDecimal probationRatio = opt(profile.getProbationRatio());
        if (probationRatio.compareTo(BigDecimal.ZERO) <= 0) {
            probationRatio = BigDecimal.ONE;
        }
        Map<String, BigDecimal> allowanceMap = parseAllowanceJson(profile.getAllowanceBaseJson());

        // 构建分段区间（处理月中入职/转正）
        List<PaySegment> segments = buildSegments(period, employee.getHireDate(),
                employee.getProbationEndDate(), probationRatio);

        List<DetailItem> detailItems = new ArrayList<>();
        boolean hasTaxItem = false;

        for (PayrollSchemeItem item : sortedItems) {
            if ("TAX".equals(item.getItemType())) {
                hasTaxItem = true;
                continue; // 个税单独计算，最后处理
            }
            DetailItem di = calcItem(employee.getId(), item, baseSalary, ssBase, hfBase, performanceBase,
                    allowanceMap, lateCount, leaveDays, overtimeHours, segments, period);
            detailItems.add(di);
        }

        // f. 累计预扣法计算个税
        DetailItem taxItem = calcTax(detailItems, employee.getId(), period,
                baseSalary, ssBase, hfBase, taxBrackets, taxYear);
        detailItems.add(taxItem);

        // g. 汇总应发（EARNING）和扣款（DEDUCTION）
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
        if (leaveDays.compareTo(new BigDecimal("15")) > 0) {
            anomalyFlags.add("LEAVE_HIGH");
        }
        if (overtimeHours.compareTo(new BigDecimal("50")) > 0) {
            anomalyFlags.add("OVERTIME_HIGH");
        }
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

    // ========================================================================
    //  分段计薪
    // ========================================================================

    /**
     * 根据员工入职日期、转正日期构建分段区间
     *
     * 支持场景：
     *   - 全月在职：1 段（比例=1）
     *   - 月中入职：2 段 [入职前=0, 入职后=1]
     *   - 月中转正：2 段 [试用期=probationRatio, 转正后=1]
     *   - 入职+转正同月：3 段 [入职前=0, 试用期=probationRatio, 转正后=1]
     *
     * 按日历天数比例计算（dailySalary × 天数 × 该段比例）。
     *
     * @param period          账期（如 "2026-07"）
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
                ratio = BigDecimal.ZERO;
                reason = "入职前";
            } else if (probationEndDate != null && !segEnd.isAfter(probationEndDate)) {
                ratio = probationRatio;
                reason = "试用期";
            } else {
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
     *
     * 全月在职且比例=1 → 直接返回 baseSalary（免分段计算）
     * 其他情况：dailySalary = baseSalary / 当月天数，按段累加
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

    // ========================================================================
    //  薪资项目计算（按 item_type 分发）
    // ========================================================================

    /**
     * 计算单个工资项目
     *
     * 根据 item_type 分发到不同计算逻辑：
     *
     * FIXED:
     *   BASE_PAY → 分段计薪
     *   POSITION_ALLOWANCE → 从 allowanceBaseJson 解析
     *
     * VARIABLE:
     *   PERFORMANCE_BONUS → performanceBase × ratio
     *   OVERTIME_PAY → hourlyRate × 倍率 × 加班小时（从台账按 rateType 分组）
     *
     * ATTENDANCE_DEDUCT:
     *   LATE_DEDUCT → 支持 FIXED/RATIO/STEP 三种扣款模式（从账套配置读取）
     *   LEAVE_DEDUCT → -(baseSalary/21.75) × leaveDays
     *
     * SS_DEDUCT / HF_DEDUCT:
     *   -基数 × ratio
     */
    private DetailItem calcItem(Long employeeId, PayrollSchemeItem item,
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
                    amount = allowanceMap.getOrDefault(item.getItemCode(),
                            allowanceMap.getOrDefault("POSITION_ALLOWANCE", BigDecimal.ZERO));
                } else {
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
                    // 加班费 = (baseSalary/21.75/8) × 倍率 × 加班小时
                    // 倍率：工作日1.5、休息日2.0、节假日3.0
                    BigDecimal dailyRate = baseSalary.divide(new BigDecimal("21.75"), 10, RoundingMode.HALF_UP);
                    BigDecimal hourlyRate = dailyRate.divide(new BigDecimal("8"), 10, RoundingMode.HALF_UP);
                    Map<Integer, BigDecimal> otByRate = new HashMap<>();
                    try {
                        List<com.company.hrms.attendance.entity.OvertimeLedger> ledgers =
                                overtimeLedgerMapper.selectByEmployeeAndPeriod(employeeId, period);
                        for (com.company.hrms.attendance.entity.OvertimeLedger l : ledgers) {
                            Integer rt = l.getRateType();
                            otByRate.merge(rt, l.getTotalHours(), BigDecimal::add);
                        }
                    } catch (Exception e) {
                        log.warn("读取加班台账失败，使用汇总加班时长", e);
                        otByRate.put(15, overtimeHours);
                    }
                    if (otByRate.isEmpty()) {
                        otByRate.put(15, overtimeHours);
                    }
                    amount = BigDecimal.ZERO;
                    for (Map.Entry<Integer, BigDecimal> entry : otByRate.entrySet()) {
                        BigDecimal multiplier;
                        switch (entry.getKey()) {
                            case 30: multiplier = new BigDecimal("3.0"); break;
                            case 20: multiplier = new BigDecimal("2.0"); break;
                            default: multiplier = new BigDecimal("1.5");
                        }
                        amount = amount.add(hourlyRate.multiply(multiplier).multiply(entry.getValue()));
                    }
                } else {
                    BigDecimal ratio = opt(item.getRatio());
                    amount = performanceBase.multiply(ratio);
                }
                break;
            }
            case "ATTENDANCE_DEDUCT": {
                type = "DEDUCTION";
                if ("LATE_DEDUCT".equals(item.getItemCode())) {
                    // 从账套读取迟到扣款配置（FIXED/RATIO/STEP）
                    BigDecimal deductAmount = BigDecimal.ZERO;
                    try {
                        PayrollScheme scheme = schemeMapper.selectById(item.getSchemeId());
                        if (scheme != null && scheme.getLateDeductionType() != null) {
                            String dedType = scheme.getLateDeductionType();
                            BigDecimal dedVal = scheme.getLateDeductionValue();
                            if ("FIXED".equals(dedType) && dedVal != null) {
                                deductAmount = dedVal;
                            } else if ("RATIO".equals(dedType) && dedVal != null) {
                                BigDecimal dailyRate = baseSalary.divide(new BigDecimal("21.75"), 10, RoundingMode.HALF_UP);
                                deductAmount = dailyRate.multiply(dedVal);
                            } else if ("STEP".equals(dedType)) {
                                String config = scheme.getLateDeductionConfig();
                                if (config != null) {
                                    ObjectMapper om = new ObjectMapper();
                                    List<Map<String, Object>> steps = om.readValue(config, List.class);
                                    long count = lateCount;
                                    for (Map<String, Object> step : steps) {
                                        String range = (String) step.get("range");
                                        Object amt = step.get("amount");
                                        if (range != null && amt != null) {
                                            if (range.endsWith("+")) {
                                                int min = Integer.parseInt(range.replace("+", ""));
                                                if (count >= min) deductAmount = new BigDecimal(amt.toString());
                                            } else {
                                                String[] parts = range.split("-");
                                                int lo = Integer.parseInt(parts[0]);
                                                int hi = Integer.parseInt(parts[1]);
                                                if (count >= lo && count <= hi) deductAmount = new BigDecimal(amt.toString());
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("读取迟到扣款配置失败，使用默认值", e);
                        deductAmount = new BigDecimal("50");
                    }
                    amount = deductAmount.negate().multiply(BigDecimal.valueOf(lateCount));
                } else if ("LEAVE_DEDUCT".equals(item.getItemCode())) {
                    // 请假扣款 = -(baseSalary/21.75) × leaveDays
                    BigDecimal dailyRate = baseSalary.divide(new BigDecimal("21.75"), 10, RoundingMode.HALF_UP);
                    amount = dailyRate.multiply(leaveDays).negate();
                } else {
                    amount = BigDecimal.ZERO;
                }
                break;
            }
            case "SS_DEDUCT": {
                type = "DEDUCTION";
                BigDecimal ratio = opt(item.getRatio());
                amount = ssBase.multiply(ratio).negate();
                break;
            }
            case "HF_DEDUCT": {
                type = "DEDUCTION";
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
        if ("BASE_PAY".equals(item.getItemCode())) {
            di.setSegments(segments);
        }
        return di;
    }

    // ========================================================================
    //  个税计算（累计预扣法）
    // ========================================================================

    /**
     * 累计预扣法计算个人所得税
     *
     * 计算公式：
     *   累计预扣应纳税所得额 = 累计收入 - 累计免税收入(5000×月数) - 累计社保公积金
     *   本期预扣税额 = 累计应纳税额 - 累计已预扣税额
     *
     * 每期计算结果存入 PayTaxYtdRecord 供下期使用。
     * 跨年自动重置（年初重新累计）。
     *
     * 税率表（2026 年）：
     *   0 ~ 36,000       → 3%
     *   36,000 ~ 144,000 → 10% (速算扣除 2,520)
     *   144,000 ~ 300,000 → 20% (速算扣除 16,920)
     *   300,000 ~ 420,000 → 25% (速算扣除 31,920)
     *   ...（最高 45%）
     */
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
                .filter(d -> "SS_DEDUCT".equals(d.getItemCode()) || "HF_DEDUCT".equals(d.getItemCode()))
                .map(d -> opt(d.getAmount()).abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 上月累计预扣数据
        String prevPeriod = getPrevPeriod(period);
        PayTaxYtdRecord prevYtd = taxYtdRecordMapper.selectByEmployeeAndPeriod(employeeId, prevPeriod);

        BigDecimal prevCumulativeTax = BigDecimal.ZERO;

        int monthNum = Integer.parseInt(period.substring(5));
        BigDecimal taxFreeBase = new BigDecimal("5000").multiply(BigDecimal.valueOf(monthNum));

        BigDecimal cumulativeTaxable;

        if (prevYtd != null && prevYtd.getTaxableIncome() != null) {
            // 有上月累计 → 正常累计
            cumulativeTaxable = prevYtd.getTaxableIncome()
                    .add(currentGross)
                    .subtract(new BigDecimal("5000"))
                    .subtract(currentSsHf);
            prevCumulativeTax = opt(prevYtd.getCumulativeTax());
        } else {
            // 首次核算或跨年重置 → 从本月开始累计
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

    /**
     * 查找个税税率档位
     *
     * 在税率表中查找累计应纳税所得额对应的档位。
     * 使用各档位的 min_taxable / max_taxable 区间判断。
     */
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
        return brackets.isEmpty() ? null : brackets.get(0);
    }

    // ========================================================================
    //  账套匹配
    // ========================================================================

    /**
     * 为员工匹配薪资账套
     *
     * 匹配策略（优先级递减）：
     *   1. 员工薪资档案中直接指定的 schemeId
     *   2. 按 Scope 匹配（DEPARTMENT / POSITION / JOB_LEVEL）
     *   3. 无范围限制的账套（适用于所有人）
     *   4. 兜底：第一个启用的账套
     */
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
                return scheme; // 无范围限制的账套适用于所有人
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

    /** 检查员工是否匹配账套的任一范围规则 */
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
            }
        }
        return false;
    }

    // ========================================================================
    //  辅助方法
    // ========================================================================

    /** 安全查询考勤数据（失败时返回 null，不阻断核算） */
    private AttendanceMonthlySummary safeGetAttendance(Long employeeId, String period) {
        try {
            return attendanceSummaryMapper.selectByEmployeeAndPeriod(employeeId, period);
        } catch (Exception e) {
            log.warn("考勤数据查询失败，使用默认值 0: employeeId={}, period={}, error={}",
                    employeeId, period, e.getMessage());
            return null;
        }
    }

    /** 构建核算失败的明细记录（包含异常标记） */
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

    // ========================================================================
    //  核算明细查询
    // ========================================================================

    /** 查询批次核算明细（分页，自动填充员工姓名） */
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

    // ========================================================================
    //  图表数据聚合（薪资趋势、部门分布）
    // ========================================================================

    /**
     * 获取图表数据（批次详情页展示）
     *
     * - costTrend: 当前批次成本趋势
     * - deptDistribution: 按部门聚合薪资总额
     */
    public ChartDataVO getChartData(Long batchId) {
        ChartDataVO vo = new ChartDataVO();

        PayrollBatch batch = batchMapper.selectById(batchId);
        if (batch == null) return vo;

        List<PayrollDetail> details = detailMapper.selectByBatchId(batchId);
        if (details.isEmpty()) return vo;

        // ------- costTrend -------
        ChartDataVO.CostTrendItem trendItem = new ChartDataVO.CostTrendItem();
        trendItem.setPeriod(batch.getPeriod());
        double totalGross = details.stream()
                .filter(d -> d.getGrossSalary() != null)
                .mapToDouble(d -> d.getGrossSalary().doubleValue())
                .sum();
        trendItem.setGrossTotal(totalGross);
        vo.getCostTrend().add(trendItem);

        // ------- deptDistribution -------
        Set<Long> empIds = details.stream()
                .map(PayrollDetail::getEmployeeId)
                .collect(Collectors.toSet());

        Map<Long, Long> empDeptMap = new HashMap<>();
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

        Map<Long, Double> deptGrossMap = new HashMap<>();
        for (PayrollDetail d : details) {
            Long deptId = empDeptMap.get(d.getEmployeeId());
            if (deptId != null && d.getGrossSalary() != null) {
                deptGrossMap.merge(deptId, d.getGrossSalary().doubleValue(), Double::sum);
            }
        }

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

    // ========================================================================
    //  手工调整 / 审批 / 发放
    // ========================================================================

    /**
     * 手工调整核算明细
     *
     * HR 在 PENDING_CONFIRM 阶段可对单个员工薪资项进行调整。
     * 调整记录写入 PayrollAdjustment 表，明细标记 manual_adjusted=1。
     */
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

    /** 提交审批：PENDING_CONFIRM → APPROVING */
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

    /** 审批通过：APPROVING → APPROVED */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        PayrollBatch batch = batchMapper.selectById(id);
        if (batch == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "批次不存在");
        if (!"APPROVING".equals(batch.getStatus())) {
            throw new BusinessException(ErrorCode.PAYROLL_IN_PROGRESS, "仅审批中状态可通过");
        }
        batch.setStatus("APPROVED");
        batchMapper.updateById(batch);
        log.info("审批通过: batchId={}", id);
    }

    /** 发放确认：APPROVED → DISTRIBUTED */
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
