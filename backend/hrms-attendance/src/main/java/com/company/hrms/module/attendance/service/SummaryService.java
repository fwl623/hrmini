package com.company.hrms.module.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.hrms.attendance.entity.AttendanceDailySummary;
import com.company.hrms.attendance.entity.AttendanceMonthLock;
import com.company.hrms.attendance.entity.HolidayCalendar;
import com.company.hrms.attendance.entity.WorkdayConfig;
import com.company.hrms.attendance.entity.AttendanceMonthlySummary;
import com.company.hrms.attendance.entity.AttendanceRecord;
import com.company.hrms.attendance.mapper.AttendanceDailySummaryMapper;
import com.company.hrms.attendance.mapper.AttendanceMonthLockMapper;
import com.company.hrms.attendance.mapper.AttendanceMonthlySummaryMapper;
import com.company.hrms.attendance.mapper.AttendanceRecordMapper;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.module.attendance.dto.AttendanceCalendarDay;
import com.company.hrms.module.attendance.dto.AttendanceCalendarVO;
import com.company.hrms.module.attendance.dto.DepartmentStatisticsVO;
import com.company.hrms.module.attendance.dto.MonthlySummaryItem;
import com.company.hrms.module.attendance.dto.MonthlySummaryVO;
import com.company.hrms.module.attendance.dto.PersonalStatisticsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 月考勤汇总 Service
 * 涵盖日终聚合、月汇总查看/锁定、统计查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryService {

    private final AttendanceDailySummaryMapper dailySummaryMapper;
    private final AttendanceMonthlySummaryMapper monthlySummaryMapper;
    private final AttendanceMonthLockMapper monthLockMapper;
    private final AttendanceRecordMapper attendanceRecordMapper;
    private final com.company.hrms.employee.mapper.EmployeeMapper employeeMapper;
    private final com.company.hrms.attendance.mapper.LeaveBalanceMapper leaveBalanceMapper;
    private final com.company.hrms.attendance.mapper.LeaveApplicationMapper leaveApplicationMapper;
    private final com.company.hrms.attendance.mapper.WorkdayConfigMapper workdayConfigMapper;
    private final com.company.hrms.attendance.mapper.HolidayCalendarMapper holidayCalendarMapper;

    // ========== 月汇总查看/锁定 ==========

    /**
     * 月汇总查看（分页）
     */
    public MonthlySummaryVO getMonthlySummary(PageParam pageParam, String period) {
        LambdaQueryWrapper<AttendanceMonthlySummary> wrapper = new LambdaQueryWrapper<AttendanceMonthlySummary>()
                .eq(AttendanceMonthlySummary::getPeriod, period)
                .orderByAsc(AttendanceMonthlySummary::getEmployeeId);

        com.baomidou.mybatisplus.core.metadata.IPage<AttendanceMonthlySummary> page =
                monthlySummaryMapper.selectPage(
                        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                                pageParam.getPage(), pageParam.getPageSize()), wrapper);

        List<MonthlySummaryItem> items = new ArrayList<>();
        for (AttendanceMonthlySummary ms : page.getRecords()) {
            MonthlySummaryItem item = new MonthlySummaryItem();
            item.setEmployeeId(ms.getEmployeeId());
            // 从 employee 表查询员工姓名
            String employeeName = String.valueOf(ms.getEmployeeId());
            try {
                com.company.hrms.employee.entity.Employee emp = employeeMapper.selectById(ms.getEmployeeId());
                if (emp != null && emp.getName() != null) {
                    employeeName = emp.getName();
                }
            } catch (Exception e) {
                log.warn("查询员工姓名失败: employeeId={}", ms.getEmployeeId(), e);
            }
            item.setEmployeeName(employeeName);
            item.setPeriod(ms.getPeriod());
            item.setShouldAttendDays(ms.getShouldAttendDays() != null ? ms.getShouldAttendDays() : 0);
            item.setActualAttendDays(ms.getActualAttendDays() != null ? ms.getActualAttendDays() : BigDecimal.ZERO);
            item.setLateCount(ms.getLateCount() != null ? ms.getLateCount() : 0);
            item.setEarlyLeaveCount(ms.getEarlyLeaveCount() != null ? ms.getEarlyLeaveCount() : 0);
            item.setAbsentDays(ms.getAbsentDays() != null ? ms.getAbsentDays() : BigDecimal.ZERO);
            item.setLeaveDays(ms.getLeaveDays() != null ? ms.getLeaveDays() : BigDecimal.ZERO);
            item.setOvertimeHours(ms.getOvertimeHours() != null ? ms.getOvertimeHours() : BigDecimal.ZERO);
            items.add(item);
        }

        // 查锁定状态
        AttendanceMonthLock lock = monthLockMapper.selectByPeriod(period);

        MonthlySummaryVO vo = new MonthlySummaryVO();
        vo.setList(items);
        vo.setTotal(page.getTotal());
        vo.setLocked(lock != null && lock.getStatus() == 20);
        return vo;
    }

    /**
     * 月汇总锁定/解锁
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateLock(String period, boolean locked, Long operatorId) {
        AttendanceMonthLock lock = monthLockMapper.selectByPeriod(period);

        if (locked) {
            if (lock == null) {
                lock = new AttendanceMonthLock();
                lock.setYearMonth(period);
                lock.setStatus(20); // LOCKED
                lock.setLockedBy(operatorId);
                lock.setLockedAt(java.time.LocalDateTime.now());
                monthLockMapper.insert(lock);
            } else {
                lock.setStatus(20);
                lock.setLockedBy(operatorId);
                lock.setLockedAt(java.time.LocalDateTime.now());
                monthLockMapper.updateById(lock);
            }
            log.info("月考勤锁定: period={}, operator={}", period, operatorId);
        } else {
            if (lock != null) {
                lock.setStatus(10); // OPEN
                monthLockMapper.updateById(lock);
                log.info("月考勤解锁: period={}, operator={}", period, operatorId);
            }
        }
    }

    /**
     * 手动生成指定月份的考勤汇总
     * 遍历该月每一天，将打卡记录聚合为日汇总，再聚合为月汇总
     */
    @Transactional(rollbackFor = Exception.class)
    public void generateMonthlySummary(String period) {
        LocalDate start = LocalDate.parse(period + "-01");
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        LocalDate current = start;
        while (!current.isAfter(end)) {
            runDailySummary(current);
            current = current.plusDays(1);
        }
        log.info("手动生成月考勤汇总完成: period={}", period);
    }

    // ========== 日终聚合 ==========

    /**
     * 执行日终聚合（v2.1 双槽位）
     * 将当日 attendance_record 按 AM(IN)/PM(OUT) 分别判定，
     * 生成 attendance_daily_summary 格式 "am:0,pm:0"
     */
    @Transactional(rollbackFor = Exception.class)
    public void runDailySummary(LocalDate date) {
        LocalDate summaryDate = date != null ? date : LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
        String period = summaryDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        // 查询当天所有员工的打卡记录
        List<AttendanceRecord> records = attendanceRecordMapper.selectList(
                new LambdaQueryWrapper<AttendanceRecord>()
                        .eq(AttendanceRecord::getPunchDate, summaryDate));

        // 加载当天已审批的请假（用于槽位覆盖判断）
        List<com.company.hrms.attendance.entity.LeaveApplication> dayLeaves = leaveApplicationMapper.selectList(
                new LambdaQueryWrapper<com.company.hrms.attendance.entity.LeaveApplication>()
                        .eq(com.company.hrms.attendance.entity.LeaveApplication::getStatus, "APPROVED")
                        .le(com.company.hrms.attendance.entity.LeaveApplication::getStartTime, summaryDate.plusDays(1).atStartOfDay())
                        .ge(com.company.hrms.attendance.entity.LeaveApplication::getEndTime, summaryDate.atStartOfDay()));

        // 按 employeeId 分组
        java.util.Map<Long, List<AttendanceRecord>> grouped = records.stream()
                .collect(java.util.stream.Collectors.groupingBy(AttendanceRecord::getEmployeeId));

        // 收集所有员工ID（有打卡+有请假的）
        java.util.Set<Long> allEmpIds = new java.util.HashSet<>(grouped.keySet());
        for (com.company.hrms.attendance.entity.LeaveApplication la : dayLeaves) {
            allEmpIds.add(la.getEmployeeId());
        }

        for (Long empId : allEmpIds) {
            List<AttendanceRecord> empRecords = grouped.getOrDefault(empId, java.util.Collections.emptyList());

            AttendanceDailySummary summary = dailySummaryMapper.selectByEmployeeAndDate(empId, summaryDate);

            // ---- v2.1 双槽位判定 ----

            // 1. 判断该员工当天的请假覆盖槽位
            boolean amLeave = false, pmLeave = false;
            for (com.company.hrms.attendance.entity.LeaveApplication la : dayLeaves) {
                if (!la.getEmployeeId().equals(empId)) continue;
                java.time.LocalTime startT = la.getStartTime().toLocalTime();
                java.time.LocalTime endT = la.getEndTime().toLocalTime();
                // 跨天请假：end > start 或 end 为午夜
                boolean isCrossDay = la.getStartTime().toLocalDate().isBefore(summaryDate)
                    || la.getEndTime().toLocalDate().isAfter(summaryDate)
                    || (la.getEndTime().toLocalDate().equals(summaryDate) && endT.equals(java.time.LocalTime.MIDNIGHT));
                // 当前日期在请假范围内才判断
                if (la.getStartTime().toLocalDate().isAfter(summaryDate) || la.getEndTime().toLocalDate().isBefore(summaryDate)) {
                    continue;
                }
                // 2026-07-21 13:00 ~ 2026-07-21 18:00：覆盖18:00 → PM请假
                // 请假时段 ∩ [00:00, 12:00) ≠ ∅ → AM请假
                // 请假时段 ∩ [12:00, 23:59] ≠ ∅ → PM请假
                if (isCrossDay || startT.isBefore(java.time.LocalTime.NOON)) {
                    // 请假从这天开始且在12点前，或跨天覆盖了整个上午
                    if (isCrossDay || (startT.isBefore(java.time.LocalTime.NOON) && endT.isAfter(java.time.LocalTime.MIDNIGHT))) {
                        amLeave = true;
                    }
                }
                if (isCrossDay || endT.isAfter(java.time.LocalTime.NOON)) {
                    pmLeave = true;
                }
            }

            // 2. 查询打卡记录
            AttendanceRecord inRecord = empRecords.stream()
                    .filter(r -> "IN".equals(r.getPunchType()))
                    .min(java.util.Comparator.comparing(AttendanceRecord::getPunchTime))
                    .orElse(null);
            AttendanceRecord outRecord = empRecords.stream()
                    .filter(r -> "OUT".equals(r.getPunchType()))
                    .max(java.util.Comparator.comparing(AttendanceRecord::getPunchTime))
                    .orElse(null);

            java.time.LocalTime workStart = java.time.LocalTime.of(9, 0);
            java.time.LocalTime workEnd = java.time.LocalTime.of(18, 0);
            int lateThreshold = 15;

            // 3. AM 槽位判定
            int amCode;
            if (amLeave) {
                amCode = 4; // 请假
            } else if (inRecord == null) {
                amCode = 5; // 缺卡
            } else {
                java.time.LocalTime t = inRecord.getPunchTime().toLocalTime();
                if (!t.isAfter(workStart)) {
                    amCode = 0; // 正常
                } else if (!t.isAfter(workStart.plusMinutes(lateThreshold))) {
                    amCode = 1; // 迟到
                } else {
                    amCode = 3; // 旷工
                }
            }

            // 4. PM 槽位判定
            int pmCode;
            if (pmLeave) {
                pmCode = 4; // 请假
            } else if (outRecord == null) {
                pmCode = 5; // 缺卡
            } else {
                java.time.LocalTime t = outRecord.getPunchTime().toLocalTime();
                if (!t.isBefore(workEnd)) {
                    pmCode = 0; // 正常
                } else if (!t.isBefore(workEnd.minusMinutes(lateThreshold))) {
                    pmCode = 2; // 早退
                } else {
                    pmCode = 3; // 旷工
                }
            }

            // 5. 写入日汇总
            if (summary == null) {
                summary = new AttendanceDailySummary();
                summary.setEmployeeId(empId);
                summary.setSummaryDate(summaryDate);
            }
            summary.setDayStatus("am:" + amCode + ",pm:" + pmCode);
            final AttendanceDailySummary finalSummary = summary;
            // 打卡时间
            empRecords.stream().filter(r -> "IN".equals(r.getPunchType()))
                    .min(java.util.Comparator.comparing(AttendanceRecord::getPunchTime))
                    .ifPresent(r -> finalSummary.setClockInTime(r.getPunchTime()));
            empRecords.stream().filter(r -> "OUT".equals(r.getPunchType()))
                    .max(java.util.Comparator.comparing(AttendanceRecord::getPunchTime))
                    .ifPresent(r -> finalSummary.setClockOutTime(r.getPunchTime()));

            if (summary.getLeaveDays() == null) summary.setLeaveDays(BigDecimal.ZERO);
            if (summary.getOvertimeHours() == null) summary.setOvertimeHours(BigDecimal.ZERO);

            if (summary.getId() == null) {
                dailySummaryMapper.insert(summary);
            } else {
                dailySummaryMapper.updateById(summary);
            }

            // 聚合到月汇总
            aggregateToMonthly(empId, period);
        }

        log.info("日终汇总完成: date={}, employeeCount={}", summaryDate, grouped.size());
    }

    // ========== 统计查询 ==========

    /**
     * 个人统计（8 项指标）
     */
    public PersonalStatisticsVO getPersonalStatistics(Long employeeId, String period) {
        PersonalStatisticsVO vo = new PersonalStatisticsVO();
        vo.setEmployeeId(employeeId);
        vo.setPeriod(period);

        // 查询员工姓名和部门
        try {
            com.company.hrms.employee.entity.Employee emp = employeeMapper.selectById(employeeId);
            if (emp != null) {
                vo.setEmployeeName(emp.getName());
                // departmentName 是 JOIN 字段，selectById 不返回，用 search 查
                List<com.company.hrms.employee.entity.Employee> empList = employeeMapper.search(
                        null, null, null, null, null, null, null,
                        " AND e.id = " + employeeId);
                if (!empList.isEmpty() && empList.get(0).getDepartmentName() != null) {
                    vo.setDepartmentName(empList.get(0).getDepartmentName());
                }
            } else {
                vo.setEmployeeName(String.valueOf(employeeId));
            }
        } catch (Exception e) {
            vo.setEmployeeName(String.valueOf(employeeId));
        }

        AttendanceMonthlySummary ms = monthlySummaryMapper.selectByEmployeeAndPeriod(employeeId, period);
        if (ms == null) {
            vo.setShouldAttendDays(0);
            vo.setActualAttendDays(BigDecimal.ZERO);
            vo.setLateCount(0);
            vo.setEarlyLeaveCount(0);
            vo.setAbsentDays(BigDecimal.ZERO);
            vo.setLeaveDays(BigDecimal.ZERO);
            vo.setOvertimeHours(BigDecimal.ZERO);
            vo.setAnnualBalance(BigDecimal.ZERO);
            return vo;
        }

        vo.setShouldAttendDays(ms.getShouldAttendDays() != null ? ms.getShouldAttendDays() : 0);
        vo.setActualAttendDays(ms.getActualAttendDays() != null ? ms.getActualAttendDays() : BigDecimal.ZERO);
        vo.setLateCount(ms.getLateCount() != null ? ms.getLateCount() : 0);
        vo.setEarlyLeaveCount(ms.getEarlyLeaveCount() != null ? ms.getEarlyLeaveCount() : 0);
        vo.setAbsentDays(ms.getAbsentDays() != null ? ms.getAbsentDays() : BigDecimal.ZERO);
        // 如果月汇总没有请假天数，但实际有已审批的请假，则从申请表累加（仅统计已审批记录）
        BigDecimal leaveDays = ms.getLeaveDays() != null ? ms.getLeaveDays() : BigDecimal.ZERO;
        if (leaveDays.compareTo(BigDecimal.ZERO) == 0) {
            try {
                java.time.LocalDate periodStart = java.time.LocalDate.parse(period + "-01");
                java.time.LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());
                List<com.company.hrms.attendance.entity.LeaveApplication> approvedLeaves =
                        leaveApplicationMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.company.hrms.attendance.entity.LeaveApplication>()
                                        .eq(com.company.hrms.attendance.entity.LeaveApplication::getEmployeeId, employeeId)
                                        .eq(com.company.hrms.attendance.entity.LeaveApplication::getStatus, "APPROVED")
                                        .ge(com.company.hrms.attendance.entity.LeaveApplication::getStartTime, periodStart.atStartOfDay())
                                        .le(com.company.hrms.attendance.entity.LeaveApplication::getStartTime, periodEnd.plusDays(1).atStartOfDay()));
                for (com.company.hrms.attendance.entity.LeaveApplication la : approvedLeaves) {
                    if (la.getLeaveDays() != null) {
                        leaveDays = leaveDays.add(la.getLeaveDays());
                    }
                }
            } catch (Exception e) {
                log.warn("查询已审批请假记录失败", e);
            }
        }
        vo.setLeaveDays(leaveDays);
        vo.setOvertimeHours(ms.getOvertimeHours() != null ? ms.getOvertimeHours() : BigDecimal.ZERO);

        // 年假余额
        try {
            com.company.hrms.attendance.entity.LeaveBalance lb = leaveBalanceMapper.selectByEmployeeAndTypeAndYear(
                    employeeId, "ANNUAL", LocalDate.now().getYear());
            vo.setAnnualBalance(lb != null && lb.getBalance() != null ? lb.getBalance() : BigDecimal.ZERO);
        } catch (Exception e) {
            log.warn("查询年假余额失败: employeeId={}", employeeId, e);
            vo.setAnnualBalance(BigDecimal.ZERO);
        }

        return vo;
    }

    /**
     * 部门统计（3 项率）
     */
    public DepartmentStatisticsVO getDepartmentStatistics(Long departmentId, String period) {
        // TODO: 通过员工服务获取部门员工列表，聚合统计
        // 当前返回占位数据
        return new DepartmentStatisticsVO(departmentId, period, 0.95, 0.02, 0.03);
    }

    /**
     * 聚合日汇总到月汇总
     */
    private void aggregateToMonthly(Long employeeId, String period) {
        LocalDate startDate = LocalDate.parse(period + "-01");
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        List<AttendanceDailySummary> dailyList = dailySummaryMapper.selectByEmployeeAndPeriod(
                employeeId, startDate, endDate);

        AttendanceMonthlySummary monthly = monthlySummaryMapper.selectByEmployeeAndPeriod(employeeId, period);
        if (monthly == null) {
            monthly = new AttendanceMonthlySummary();
            monthly.setEmployeeId(employeeId);
            monthly.setPeriod(period);
        }

        int shouldAttendDays = 0;
        BigDecimal actualAttendDays = BigDecimal.ZERO;
        int lateCount = 0;
        int earlyLeaveCount = 0;
        BigDecimal absentDays = BigDecimal.ZERO;
        BigDecimal leaveDays = BigDecimal.ZERO;
        BigDecimal overtimeHours = BigDecimal.ZERO;

        for (AttendanceDailySummary daily : dailyList) {
            shouldAttendDays++;
            if ("NORMAL".equals(daily.getDayStatus()) || "LATE".equals(daily.getDayStatus())
                    || "EARLY_LEAVE".equals(daily.getDayStatus())) {
                actualAttendDays = actualAttendDays.add(BigDecimal.ONE);
            } else if ("ABSENT_HALF".equals(daily.getDayStatus())) {
                actualAttendDays = actualAttendDays.add(BigDecimal.valueOf(0.5));
                absentDays = absentDays.add(BigDecimal.valueOf(0.5));
            } else if ("ABSENT".equals(daily.getDayStatus())) {
                absentDays = absentDays.add(BigDecimal.ONE);
            } else if ("LEAVE".equals(daily.getDayStatus())) {
                leaveDays = leaveDays.add(daily.getLeaveDays());
            }

            if ("LATE".equals(daily.getDayStatus())) lateCount++;
            if ("EARLY_LEAVE".equals(daily.getDayStatus())) earlyLeaveCount++;
            overtimeHours = overtimeHours.add(daily.getOvertimeHours());
        }

        monthly.setShouldAttendDays(shouldAttendDays);
        monthly.setActualAttendDays(actualAttendDays);
        monthly.setLateCount(lateCount);
        monthly.setEarlyLeaveCount(earlyLeaveCount);
        monthly.setAbsentDays(absentDays);
        monthly.setLeaveDays(leaveDays);
        monthly.setOvertimeHours(overtimeHours);

        if (monthly.getId() == null) {
            monthlySummaryMapper.insert(monthly);
        } else {
            monthlySummaryMapper.updateById(monthly);
        }
    }

    // ========== 考勤日历（门户） ==========

    /**
     * 获取员工指定月份的考勤日历
     * 当日汇总不存在时，自动检查已审批通过的请假记录
     */
    public AttendanceCalendarVO getCalendar(Long employeeId, String period) {
        LocalDate start = LocalDate.parse(period + "-01");
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<AttendanceDailySummary> dailyList = dailySummaryMapper.selectByEmployeeAndPeriod(
                employeeId, start, end);

        // 构建日期 → 状态映射
        java.util.Map<LocalDate, AttendanceDailySummary> summaryMap = new java.util.HashMap<>();
        for (AttendanceDailySummary ds : dailyList) {
            summaryMap.put(ds.getSummaryDate(), ds);
        }

        // 加载工作日配置和节假日，用于判断日期是否为工作日
        List<WorkdayConfig> wkConfigs = workdayConfigMapper.selectList(null);
        java.util.Set<Integer> workdaySet = wkConfigs.stream()
                .filter(w -> w.getIsWorkday() == 1)
                .map(WorkdayConfig::getDayOfWeek)
                .collect(java.util.stream.Collectors.toSet());
        List<HolidayCalendar> holidays = holidayCalendarMapper.selectList(null);
        java.util.Set<java.time.LocalDate> holidayDates = holidays.stream()
                .map(HolidayCalendar::getHolidayDate)
                .collect(java.util.stream.Collectors.toSet());

        // 加载该员工当月已审批通过的请假记录（已驳回/已撤销/待审批的不计入）
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        List<com.company.hrms.attendance.entity.LeaveApplication> approvedLeaves =
                leaveApplicationMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.company.hrms.attendance.entity.LeaveApplication>()
                                .eq(com.company.hrms.attendance.entity.LeaveApplication::getEmployeeId, employeeId)
                                .eq(com.company.hrms.attendance.entity.LeaveApplication::getStatus, "APPROVED")
                                .ge(com.company.hrms.attendance.entity.LeaveApplication::getStartTime, start.atStartOfDay())
                                .le(com.company.hrms.attendance.entity.LeaveApplication::getStartTime, end.plusDays(1).atStartOfDay()));
        // 构建请假日期集合
        // 规则：仅已审批 + 仅工作日 + 非未来日期
        java.util.Set<java.time.LocalDate> leaveDateSet = new java.util.HashSet<>();
        for (com.company.hrms.attendance.entity.LeaveApplication la : approvedLeaves) {
            java.time.LocalDate laStart = la.getStartTime().toLocalDate();
            java.time.LocalDate laEnd = la.getEndTime().toLocalDate();
            // 结束时间为午夜00:00时不包含结束日
            if (la.getEndTime().toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
                laEnd = laEnd.minusDays(1);
            }
            java.time.LocalDate d = laStart;
            while (!d.isAfter(laEnd)) {
                // 条件1：仅已过去的日期
                // 条件2：仅工作日（非周末、非节假日）
                if (!d.isAfter(today)
                        && workdaySet.contains(d.getDayOfWeek().getValue())
                        && !holidayDates.contains(d)) {
                    leaveDateSet.add(d);
                }
                d = d.plusDays(1);
            }
        }

        List<AttendanceCalendarDay> days = new ArrayList<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            AttendanceDailySummary ds = summaryMap.get(current);
            AttendanceCalendarDay day = new AttendanceCalendarDay();
            day.setDate(current.toString());

            if (ds != null) {
                // v2.1: 解析双槽位格式 "am:0,pm:0"
                String displayStatus = parseDualSlotStatus(ds.getDayStatus());
                day.setDayStatus(displayStatus);
                day.setClockInTime(ds.getClockInTime() != null
                        ? ds.getClockInTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : null);
                day.setClockOutTime(ds.getClockOutTime() != null
                        ? ds.getClockOutTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : null);
            } else if (leaveDateSet.contains(current)) {
                // 无汇总但有已审批请假 → 标记为 LEAVE
                day.setDayStatus("LEAVE");
            } else if (!current.isAfter(today)
                    && workdaySet.contains(current.getDayOfWeek().getValue())
                    && !holidayDates.contains(current)) {
                // Fix3: 已过去的工作日，无汇总、无请假 → 缺勤
                day.setDayStatus("ABSENT");
            } else {
                // 非工作日或未来日期
                day.setDayStatus("--");
            }

            days.add(day);
            current = current.plusDays(1);
        }

        return new AttendanceCalendarVO(start.getYear(), start.getMonthValue(), days);
    }

    // ========== v2.1 双槽位工具方法 ==========

    /**
     * 解析双槽位状态 "am:0,pm:0" → 展示用单状态（最差槽位优先）
     * 兼容旧格式（无 am:/pm: 前缀时原样返回）
     */
    private String parseDualSlotStatus(String raw) {
        if (raw == null) return "--";
        if (!raw.startsWith("am:") && !raw.startsWith("pm:")) {
            // 旧格式兼容
            return raw;
        }
        try {
            String[] parts = raw.split(",");
            int amCode = Integer.parseInt(parts[0].split(":")[1]);
            int pmCode = Integer.parseInt(parts[1].split(":")[1]);

            // 按优先级返回展示状态：请假 > 旷工 > 缺卡 > 迟到/早退 > 正常
            if (amCode == 4 || pmCode == 4) return "LEAVE";
            if (amCode == 3 || pmCode == 3) return "ABSENT";
            if (amCode == 5 && pmCode == 5) return "ABSENT"; // 双缺卡→缺勤展示
            if (amCode == 1 || pmCode == 2) return (amCode == 1 ? "LATE" : "EARLY_LEAVE");
            if (amCode == 5 || pmCode == 5) return "MISSING_IN"; // 单缺卡
            return "NORMAL";
        } catch (Exception e) {
            return raw;
        }
    }
}
