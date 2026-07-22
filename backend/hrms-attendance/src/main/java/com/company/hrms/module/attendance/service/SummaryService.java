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
 *
 * 核心功能：
 * 1. 日终聚合（每日凌晨 02:00 定时执行，v2.1 双槽位判定）
 * 2. 月汇总查看 / 锁定 / 生成
 * 3. 考勤统计查询（个人 8 项指标、部门 3 项率）
 * 4. 考勤日历（门户端展示）
 *
 * 数据流：attendance_record → runDailySummary → attendance_daily_summary
 *                                       ↓
 *                              aggregateToMonthly → attendance_monthly_summary
 *
 * v2.1 双槽位格式 "am:code,pm:code"：
 *   code: 0=正常, 1=迟到, 2=早退, 3=旷工, 4=请假, 5=缺卡
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
    private final com.company.hrms.attendance.mapper.AttendanceGroupMapper attendanceGroupMapper;
    private final com.company.hrms.attendance.mapper.AttendanceGroupMemberMapper attendanceGroupMemberMapper;
    private final com.company.hrms.attendance.mapper.WorkdayConfigMapper workdayConfigMapper;
    private final com.company.hrms.attendance.mapper.HolidayCalendarMapper holidayCalendarMapper;

    // ========================================================================
    //  月汇总查看/锁定
    // ========================================================================

    /**
     * 月汇总查看（分页）
     *
     * 按账期查询所有员工的月考勤汇总数据。
     * 返回各员工当月的应出勤、实际出勤、迟到/早退/旷工/请假/加班等指标。
     * 同时返回该月的考勤锁定状态。
     *
     * @param pageParam 分页参数
     * @param period    账期（格式 YYYY-MM）
     * @return 月汇总视图（含列表 + 锁定状态）
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
            // 查询员工姓名
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
     *
     * 锁定后该月考勤数据冻结，不可再补卡或修改。
     * 薪资核算时通常要求考勤月已锁定，保证核算数据的稳定性。
     *
     * @param period     账期（格式 YYYY-MM）
     * @param locked     true=锁定, false=解锁
     * @param operatorId 操作人 ID
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
     *
     * 遍历该月每一天执行 runDailySummary，逐日将打卡记录聚合为日汇总，
     * 再通过 aggregateToMonthly 聚合为月汇总。
     * 用于 HR 手动触发重新生成，或系统升级后数据修复。
     *
     * @param period 账期（格式 YYYY-MM）
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

    // ========================================================================
    //  日终聚合（v2.1 双槽位）
    // ========================================================================

    /**
     * 执行日终聚合（v2.1 双槽位）
     *
     * 每日凌晨 02:00 定时执行（@Scheduled cron = "0 0 2 * * ?"）。
     * 也支持打卡时实时触发（updateDailySummaryInMemory）。
     *
     * 处理逻辑：
     *   1. 查询当天所有员工的打卡记录
     *   2. 查询当天已审批的请假记录（判断槽位覆盖）
     *   3. 逐员工进行双槽位判定（AM 上班精度 / PM 下班精度）
     *   4. 写入/更新 attendance_daily_summary
     *   5. 聚合到 attendance_monthly_summary
     *
     * 默认汇总日期为前一天的记录（date=null 时取 today-1）。
     *
     * @param date 汇总日期（null=汇总昨天）
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

        // 收集所有员工ID（有打卡+有请假的），确保请假员工也有日汇总
        java.util.Set<Long> allEmpIds = new java.util.HashSet<>(grouped.keySet());
        for (com.company.hrms.attendance.entity.LeaveApplication la : dayLeaves) {
            allEmpIds.add(la.getEmployeeId());
        }

        for (Long empId : allEmpIds) {
            List<AttendanceRecord> empRecords = grouped.getOrDefault(empId, java.util.Collections.emptyList());

            AttendanceDailySummary summary = dailySummaryMapper.selectByEmployeeAndDate(empId, summaryDate);

            // ---- v2.1 双槽位判定 ----

            // 1. 判断该员工当天的请假覆盖槽位
            // AM 覆盖：请假时段 ∩ [00:00, 12:00) ≠ ∅
            // PM 覆盖：请假时段 ∩ [12:00, 23:59] ≠ ∅
            boolean amLeave = false, pmLeave = false;
            for (com.company.hrms.attendance.entity.LeaveApplication la : dayLeaves) {
                if (!la.getEmployeeId().equals(empId)) continue;
                java.time.LocalDate laEnd2 = la.getEndTime().toLocalDate();
                if (la.getEndTime().toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
                    laEnd2 = laEnd2.minusDays(1);
                }
                if (summaryDate.isAfter(laEnd2) || summaryDate.isBefore(la.getStartTime().toLocalDate())) {
                    continue;
                }
                java.time.LocalTime startT = la.getStartTime().toLocalTime();
                java.time.LocalTime endT = la.getEndTime().toLocalTime();
                boolean isCrossDay = la.getStartTime().toLocalDate().isBefore(summaryDate)
                    || laEnd2.isAfter(summaryDate);

                if (isCrossDay || startT.isBefore(java.time.LocalTime.NOON)) {
                    if (isCrossDay || endT.equals(java.time.LocalTime.MIDNIGHT)
                            || (startT.isBefore(java.time.LocalTime.NOON) && endT.isAfter(java.time.LocalTime.MIDNIGHT))) {
                        amLeave = true;
                    }
                }
                if (isCrossDay || endT.isAfter(java.time.LocalTime.NOON) || endT.equals(java.time.LocalTime.MIDNIGHT)) {
                    pmLeave = true;
                }
            }

            // 2. 查询打卡记录（IN取最早，OUT取最晚）
            AttendanceRecord inRecord = empRecords.stream()
                    .filter(r -> "IN".equals(r.getPunchType()))
                    .min(java.util.Comparator.comparing(AttendanceRecord::getPunchTime))
                    .orElse(null);
            AttendanceRecord outRecord = empRecords.stream()
                    .filter(r -> "OUT".equals(r.getPunchType()))
                    .max(java.util.Comparator.comparing(AttendanceRecord::getPunchTime))
                    .orElse(null);

            // 3. 读取员工考勤组配置（默认 09:00-18:00，迟到阈值 15 分钟）
            java.time.LocalTime workStart = java.time.LocalTime.of(9, 0);
            java.time.LocalTime workEnd = java.time.LocalTime.of(18, 0);
            int lateThreshold = 15;
            boolean isFlexible = false;
            java.time.LocalTime flexEarliest = null;
            java.time.LocalTime flexLatest = null;
            try {
                com.company.hrms.attendance.entity.AttendanceGroupMember agm = attendanceGroupMemberMapper.selectById(empId);
                if (agm != null) {
                    com.company.hrms.attendance.entity.AttendanceGroup grp = attendanceGroupMapper.selectById(agm.getGroupId());
                    if (grp != null) {
                        if (grp.getWorkStartTime() != null) workStart = grp.getWorkStartTime();
                        if (grp.getWorkEndTime() != null) workEnd = grp.getWorkEndTime();
                        if (grp.getLateThresholdMinutes() != null) lateThreshold = grp.getLateThresholdMinutes();
                        if ("FLEXIBLE".equals(grp.getShiftType())) {
                            isFlexible = true;
                            flexEarliest = grp.getFlexStartEarliest();
                            flexLatest = grp.getFlexStartLatest();
                        }
                    }
                }
            } catch (Exception e) { log.warn("读取考勤组配置失败", e); }

            // 4. AM 槽位判定（支持弹性班）
            // code: 0=正常, 1=迟到, 3=旷工, 4=请假, 5=缺卡
            int amCode;
            if (amLeave) {
                amCode = 4;
            } else if (inRecord == null) {
                amCode = 5;
            } else {
                java.time.LocalTime t = inRecord.getPunchTime().toLocalTime();
                if (isFlexible && flexEarliest != null && flexLatest != null) {
                    amCode = (!t.isBefore(flexEarliest) && !t.isAfter(flexLatest)) ? 0 : 1;
                } else {
                    if (!t.isAfter(workStart)) {
                        amCode = 0;
                    } else if (!t.isAfter(workStart.plusMinutes(lateThreshold))) {
                        amCode = 1;
                    } else {
                        amCode = 3;
                    }
                }
            }

            // 5. PM 槽位判定
            int pmCode;
            if (pmLeave) {
                pmCode = 4;
            } else if (outRecord == null) {
                pmCode = 5;
            } else {
                java.time.LocalTime t = outRecord.getPunchTime().toLocalTime();
                if (!t.isBefore(workEnd)) {
                    pmCode = 0;
                } else if (!t.isBefore(workEnd.minusMinutes(lateThreshold))) {
                    pmCode = 2;
                } else {
                    pmCode = 3;
                }
            }

            // 6. 写入/更新日汇总
            if (summary == null) {
                summary = new AttendanceDailySummary();
                summary.setEmployeeId(empId);
                summary.setSummaryDate(summaryDate);
            }
            summary.setDayStatus("am:" + amCode + ",pm:" + pmCode);
            final AttendanceDailySummary finalSummary = summary;
            // 记录打卡时间
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

    // ========================================================================
    //  统计查询
    // ========================================================================

    /**
     * 个人考勤统计（8 项指标）
     *
     * 查询指定员工在指定月份的考勤数据：
     *   应出勤、实际出勤、迟到次数、早退次数、
     *   旷工天数、请假天数、加班时长、年假余额
     *
     * 如果月汇总中请假天数为 0，会尝试从已审批的请假记录中累加。
     *
     * @param employeeId 员工 ID
     * @param period     统计月份（格式 YYYY-MM）
     * @return 8 项指标的个人统计 VO
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
                // departmentName 是 JOIN 字段，用 search 获取
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
            // 无汇总数据时返回全 0 值
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
        // 如果月汇总请假天数为 0，尝试从已审批的请假记录中累加（兜底）
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

        // 查询年假余额
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
     * 部门考勤统计（3 项率）
     *
     * 查询指定部门在指定月份的出勤率、迟到率、请假率。
     * 从 attendance_monthly_summary 聚合真实数据。
     *
     * @param departmentId 部门 ID
     * @param period       统计月份（格式 YYYY-MM）
     * @return 出勤率、迟到率、请假率
     */
    public DepartmentStatisticsVO getDepartmentStatistics(Long departmentId, String period) {
        // 查询部门下所有在职员工
        List<com.company.hrms.employee.entity.Employee> employees = employeeMapper.search(
                null, null, null, java.util.List.of(10, 20), null, null, null,
                " AND e.department_id = " + departmentId);
        if (employees.isEmpty()) {
            return new DepartmentStatisticsVO(departmentId, period, 0.0, 0.0, 0.0);
        }

        int totalShouldDays = 0;
        int totalActualDays = 0;
        int totalLateCount = 0;
        int totalLeaveDays = 0;

        for (com.company.hrms.employee.entity.Employee emp : employees) {
            AttendanceMonthlySummary ms = monthlySummaryMapper.selectByEmployeeAndPeriod(emp.getId(), period);
            if (ms != null) {
                totalShouldDays += ms.getShouldAttendDays() != null ? ms.getShouldAttendDays() : 0;
                totalActualDays += ms.getActualAttendDays() != null ? ms.getActualAttendDays().intValue() : 0;
                totalLateCount += ms.getLateCount() != null ? ms.getLateCount() : 0;
                totalLeaveDays += ms.getLeaveDays() != null ? ms.getLeaveDays().intValue() : 0;
            }
        }

        double attendanceRate = totalShouldDays > 0
                ? (double) totalActualDays / totalShouldDays : 0.0;
        double lateRate = totalShouldDays > 0
                ? (double) totalLateCount / totalShouldDays : 0.0;
        double leaveRate = totalShouldDays > 0
                ? (double) totalLeaveDays / totalShouldDays : 0.0;

        return new DepartmentStatisticsVO(departmentId, period,
                java.math.BigDecimal.valueOf(attendanceRate).setScale(4, java.math.RoundingMode.HALF_UP).doubleValue(),
                java.math.BigDecimal.valueOf(lateRate).setScale(4, java.math.RoundingMode.HALF_UP).doubleValue(),
                java.math.BigDecimal.valueOf(leaveRate).setScale(4, java.math.RoundingMode.HALF_UP).doubleValue());
    }

    // ========================================================================
    //  聚合（日 → 月）
    // ========================================================================

    /**
     * 聚合日汇总到月汇总
     *
     * 遍历当月所有日汇总记录，按 v2.1 双槽位格式累加各项指标：
     *   - 实际出勤：双槽位都正常=1天，单槽位正常=0.5天
     *   - 迟到次数：AM code=1 的次数
     *   - 早退次数：PM code=2 的次数
     *   - 旷工天数：AM/PM code=3 各计 0.5 天
     *   - 请假天数：AM/PM code=4 时取 leave_days 字段
     *   - 加班时长：累加 overtime_hours
     *
     * 应出勤天数从 WorkdayConfig + HolidayCalendar 动态计算，
     * 不依赖日汇总数量（避免因某天无汇总记录导致统计偏差）。
     *
     * @param employeeId 员工 ID
     * @param period     账期（格式 YYYY-MM）
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

        // 动态计算当月工作日数（从配置读取，不依赖日汇总数量）
        int shouldAttendDays = 0;
        try {
            java.util.Set<Integer> wkSet = workdayConfigMapper.selectList(null).stream()
                    .filter(w -> w.getIsWorkday() == 1)
                    .map(WorkdayConfig::getDayOfWeek)
                    .collect(java.util.stream.Collectors.toSet());
            java.util.Set<java.time.LocalDate> holSet = holidayCalendarMapper.selectList(null).stream()
                    .map(HolidayCalendar::getHolidayDate)
                    .collect(java.util.stream.Collectors.toSet());
            java.time.LocalDate d = startDate;
            while (!d.isAfter(endDate)) {
                if (wkSet.contains(d.getDayOfWeek().getValue()) && !holSet.contains(d)) {
                    shouldAttendDays++;
                }
                d = d.plusDays(1);
            }
        } catch (Exception e) {
            log.warn("计算工作日数失败，回退到日汇总数量", e);
            shouldAttendDays = dailyList.size();
        }

        BigDecimal actualAttendDays = BigDecimal.ZERO;
        int lateCount = 0;
        int earlyLeaveCount = 0;
        BigDecimal absentDays = BigDecimal.ZERO;
        BigDecimal leaveDays = BigDecimal.ZERO;
        BigDecimal overtimeHours = BigDecimal.ZERO;

        for (AttendanceDailySummary daily : dailyList) {
            String raw = daily.getDayStatus();
            int amCode = -1, pmCode = -1;
            boolean isNewFormat = (raw != null && raw.startsWith("am:"));
            if (isNewFormat) {
                try {
                    String[] parts = raw.split(",");
                    amCode = Integer.parseInt(parts[0].split(":")[1]);
                    pmCode = Integer.parseInt(parts[1].split(":")[1]);
                } catch (Exception e) {}
            }

            if (isNewFormat && (amCode == 4 || pmCode == 4)) {
                // v2.1 请假槽位：按 leave_days 计
                leaveDays = leaveDays.add(daily.getLeaveDays() != null ? daily.getLeaveDays() : BigDecimal.ZERO);
            } else if (isNewFormat) {
                // v2.1 正常/迟到/早退/旷工
                boolean amOk = (amCode == 0 || amCode == 1);
                boolean pmOk = (pmCode == 0 || pmCode == 2);
                if (amOk && pmOk) actualAttendDays = actualAttendDays.add(BigDecimal.ONE);
                else if (amOk || pmOk) actualAttendDays = actualAttendDays.add(BigDecimal.valueOf(0.5));
                if (amCode == 1) lateCount++;
                if (pmCode == 2) earlyLeaveCount++;
                if (amCode == 3) absentDays = absentDays.add(BigDecimal.valueOf(0.5));
                if (pmCode == 3) absentDays = absentDays.add(BigDecimal.valueOf(0.5));
            } else {
                // 旧格式兼容（NORMAL/LATE/EARLY_LEAVE/ABSENT_HALF/ABSENT/LEAVE）
                if ("NORMAL".equals(raw) || "LATE".equals(raw) || "EARLY_LEAVE".equals(raw)) {
                    actualAttendDays = actualAttendDays.add(BigDecimal.ONE);
                } else if ("ABSENT_HALF".equals(raw)) {
                    actualAttendDays = actualAttendDays.add(BigDecimal.valueOf(0.5));
                    absentDays = absentDays.add(BigDecimal.valueOf(0.5));
                } else if ("ABSENT".equals(raw)) {
                    absentDays = absentDays.add(BigDecimal.ONE);
                } else if ("LEAVE".equals(raw)) {
                    leaveDays = leaveDays.add(daily.getLeaveDays());
                }
                if ("LATE".equals(raw)) lateCount++;
                if ("EARLY_LEAVE".equals(raw)) earlyLeaveCount++;
            }
            overtimeHours = overtimeHours.add(daily.getOvertimeHours() != null ? daily.getOvertimeHours() : BigDecimal.ZERO);
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

    // ========================================================================
    //  考勤日历（门户端）
    // ========================================================================

    /**
     * 获取员工指定月份的考勤日历（门户端展示）
     *
     * 返回当月每日的考勤状态色块数据，用于日历视图渲染。
     * 当日汇总不存在时，自动检查已审批通过的请假记录。
     * 已过去的工作日既无汇总又无请假 → 标记为 ABSENT（缺勤）。
     *
     * @param employeeId 员工 ID
     * @param period     月份（格式 YYYY-MM）
     * @return 考勤日历视图
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

        // 加载工作日配置和节假日
        List<WorkdayConfig> wkConfigs = workdayConfigMapper.selectList(null);
        java.util.Set<Integer> workdaySet = wkConfigs.stream()
                .filter(w -> w.getIsWorkday() == 1)
                .map(WorkdayConfig::getDayOfWeek)
                .collect(java.util.stream.Collectors.toSet());
        List<HolidayCalendar> holidays = holidayCalendarMapper.selectList(null);
        java.util.Set<java.time.LocalDate> holidayDates = holidays.stream()
                .map(HolidayCalendar::getHolidayDate)
                .collect(java.util.stream.Collectors.toSet());

        // 加载该员工当月已审批通过的请假记录
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        List<com.company.hrms.attendance.entity.LeaveApplication> approvedLeaves =
                leaveApplicationMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.company.hrms.attendance.entity.LeaveApplication>()
                                .eq(com.company.hrms.attendance.entity.LeaveApplication::getEmployeeId, employeeId)
                                .eq(com.company.hrms.attendance.entity.LeaveApplication::getStatus, "APPROVED")
                                .ge(com.company.hrms.attendance.entity.LeaveApplication::getStartTime, start.atStartOfDay())
                                .le(com.company.hrms.attendance.entity.LeaveApplication::getStartTime, end.plusDays(1).atStartOfDay()));
        // 构建请假日期集合（仅已审批 + 仅工作日 + 非未来日期）
        java.util.Set<java.time.LocalDate> leaveDateSet = new java.util.HashSet<>();
        for (com.company.hrms.attendance.entity.LeaveApplication la : approvedLeaves) {
            java.time.LocalDate laStart = la.getStartTime().toLocalDate();
            java.time.LocalDate laEnd = la.getEndTime().toLocalDate();
            if (la.getEndTime().toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
                laEnd = laEnd.minusDays(1);
            }
            java.time.LocalDate d = laStart;
            while (!d.isAfter(laEnd)) {
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
                // 有日汇总：解析双槽位格式
                String displayStatus = parseDualSlotStatus(ds.getDayStatus());
                day.setDayStatus(displayStatus);
                day.setClockInTime(ds.getClockInTime() != null
                        ? ds.getClockInTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : null);
                day.setClockOutTime(ds.getClockOutTime() != null
                        ? ds.getClockOutTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : null);
            } else if (leaveDateSet.contains(current)) {
                // 无汇总但有已审批请假 → LEAVE
                day.setDayStatus("LEAVE");
            } else if (current.isBefore(today)
                    && workdaySet.contains(current.getDayOfWeek().getValue())
                    && !holidayDates.contains(current)) {
                // 已过去的工作日，无汇总、无请假 → 缺勤
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

    // ========================================================================
    //  双槽位工具方法
    // ========================================================================

    /**
     * 解析双槽位状态 "am:code,pm:code" → 展示用单状态
     *
     * 优先级：请假 > 旷工 > 缺卡 > 迟到/早退 > 正常
     * 兼容旧格式（无 am:/pm: 前缀时原样返回）。
     *
     * @param raw 原始状态字符串（如 "am:0,pm:0"）
     * @return 展示状态（NORMAL / LATE / EARLY_LEAVE / ABSENT / LEAVE / MISSING_IN）
     */
    private String parseDualSlotStatus(String raw) {
        if (raw == null) return "--";
        if (!raw.startsWith("am:") && !raw.startsWith("pm:")) {
            return raw; // 旧格式兼容
        }
        try {
            String[] parts = raw.split(",");
            int amCode = Integer.parseInt(parts[0].split(":")[1]);
            int pmCode = Integer.parseInt(parts[1].split(":")[1]);

            if (amCode == 4 || pmCode == 4) return "LEAVE";
            if (amCode == 3 || pmCode == 3) return "ABSENT";
            if (amCode == 5 && pmCode == 5) return "ABSENT"; // 双缺卡→缺勤
            if (amCode == 1 || pmCode == 2) return (amCode == 1 ? "LATE" : "EARLY_LEAVE");
            if (amCode == 5 || pmCode == 5) return "MISSING_IN";
            return "NORMAL";
        } catch (Exception e) {
            return raw;
        }
    }
}
