package com.company.hrms.module.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.hrms.attendance.entity.AttendanceDailySummary;
import com.company.hrms.attendance.entity.AttendanceMonthLock;
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
            item.setEmployeeName(String.valueOf(ms.getEmployeeId())); // TODO: Feign
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

    // ========== 日终聚合 ==========

    /**
     * 执行日终聚合
     * 将当日 attendance_record 聚合为 attendance_daily_summary，
     * 再聚合进 attendance_monthly_summary
     *
     * @param date 汇总日期，null 表示昨天
     */
    @Transactional(rollbackFor = Exception.class)
    public void runDailySummary(LocalDate date) {
        LocalDate summaryDate = date != null ? date : LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
        String period = summaryDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        // 查询当天所有员工的打卡记录
        List<AttendanceRecord> records = attendanceRecordMapper.selectList(
                new LambdaQueryWrapper<AttendanceRecord>()
                        .eq(AttendanceRecord::getPunchDate, summaryDate));

        // 按 employeeId 分组
        java.util.Map<Long, List<AttendanceRecord>> grouped = records.stream()
                .collect(java.util.stream.Collectors.groupingBy(AttendanceRecord::getEmployeeId));

        for (java.util.Map.Entry<Long, List<AttendanceRecord>> entry : grouped.entrySet()) {
            Long empId = entry.getKey();
            List<AttendanceRecord> empRecords = entry.getValue();

            AttendanceDailySummary summary = dailySummaryMapper.selectByEmployeeAndDate(empId, summaryDate);
            if (summary == null) {
                summary = new AttendanceDailySummary();
                summary.setEmployeeId(empId);
                summary.setSummaryDate(summaryDate);
            }

            // 计算日考勤状态
            boolean hasIn = empRecords.stream().anyMatch(r -> "IN".equals(r.getPunchType()));
            boolean hasOut = empRecords.stream().anyMatch(r -> "OUT".equals(r.getPunchType()));

            String dayStatus;
            if (hasIn && hasOut) {
                // 取最差的打卡状态
                dayStatus = empRecords.stream()
                        .map(AttendanceRecord::getPunchStatus)
                        .max(java.util.Comparator.comparingInt(s -> {
                            if ("ABSENT_HALF".equals(s)) return 3;
                            if ("LATE".equals(s)) return 2;
                            if ("EARLY_LEAVE".equals(s)) return 2;
                            return 1; // NORMAL
                        }))
                        .orElse("NORMAL");
            } else if (hasIn) {
                dayStatus = "MISSING_OUT";
            } else if (hasOut) {
                dayStatus = "MISSING_IN";
            } else {
                dayStatus = "ABSENT";
            }

            summary.setDayStatus(dayStatus);
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
        AttendanceMonthlySummary ms = monthlySummaryMapper.selectByEmployeeAndPeriod(employeeId, period);
        if (ms == null) {
            return new PersonalStatisticsVO(employeeId, period, 0, BigDecimal.ZERO, 0, 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        // 查年假余额
        com.company.hrms.attendance.entity.LeaveBalance lb = null;
        // TODO: 通过 LeaveBalanceMapper 查询年假余额
        BigDecimal annualBalance = BigDecimal.ZERO;

        return new PersonalStatisticsVO(
                employeeId, period,
                ms.getShouldAttendDays() != null ? ms.getShouldAttendDays() : 0,
                ms.getActualAttendDays() != null ? ms.getActualAttendDays() : BigDecimal.ZERO,
                ms.getLateCount() != null ? ms.getLateCount() : 0,
                ms.getEarlyLeaveCount() != null ? ms.getEarlyLeaveCount() : 0,
                ms.getAbsentDays() != null ? ms.getAbsentDays() : BigDecimal.ZERO,
                ms.getLeaveDays() != null ? ms.getLeaveDays() : BigDecimal.ZERO,
                ms.getOvertimeHours() != null ? ms.getOvertimeHours() : BigDecimal.ZERO,
                annualBalance
        );
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
     *
     * @param employeeId 员工 ID
     * @param period     月份 yyyy-MM
     * @return 考勤日历，包含当月每日状态
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

        List<AttendanceCalendarDay> days = new ArrayList<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            AttendanceDailySummary ds = summaryMap.get(current);
            AttendanceCalendarDay day = new AttendanceCalendarDay();
            day.setDate(current.toString());

            if (ds != null) {
                day.setDayStatus(ds.getDayStatus());
                day.setClockInTime(ds.getClockInTime() != null
                        ? ds.getClockInTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : null);
                day.setClockOutTime(ds.getClockOutTime() != null
                        ? ds.getClockOutTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : null);
            } else {
                // 无汇总记录 → 非工作日或尚未生成汇总
                day.setDayStatus("--");
            }

            days.add(day);
            current = current.plusDays(1);
        }

        return new AttendanceCalendarVO(start.getYear(), start.getMonthValue(), days);
    }
}
