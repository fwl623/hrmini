package com.company.hrms.module.attendance.job;

import com.company.hrms.attendance.entity.LeaveApplication;
import com.company.hrms.attendance.entity.LeaveBalance;
import com.company.hrms.attendance.entity.AttendanceMonthlySummary;
import com.company.hrms.attendance.entity.OvertimeApplication;
import com.company.hrms.attendance.entity.OvertimeLedger;
import com.company.hrms.attendance.entity.AttendanceSupplement;
import com.company.hrms.attendance.mapper.LeaveApplicationMapper;
import com.company.hrms.attendance.mapper.LeaveBalanceMapper;
import com.company.hrms.attendance.mapper.OvertimeApplicationMapper;
import com.company.hrms.attendance.mapper.OvertimeLedgerMapper;
import com.company.hrms.attendance.mapper.AttendanceSupplementMapper;
import com.company.hrms.attendance.mapper.AttendanceRecordMapper;
import com.company.hrms.attendance.entity.AttendanceRecord;
import com.company.hrms.attendance.entity.AttendanceDailySummary;
import com.company.hrms.attendance.mapper.AttendanceDailySummaryMapper;
import com.company.hrms.common.event.ApprovalCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.event.EventListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 审批完成事件监听器
 * 监听 ApprovalCompletedEvent，处理请假/加班/补卡审批结果
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalEventListener {

    private final LeaveApplicationMapper leaveApplicationMapper;
    private final LeaveBalanceMapper leaveBalanceMapper;
    private final OvertimeApplicationMapper overtimeApplicationMapper;
    private final OvertimeLedgerMapper overtimeLedgerMapper;
    private final AttendanceSupplementMapper attendanceSupplementMapper;
    private final AttendanceRecordMapper attendanceRecordMapper;
    private final AttendanceDailySummaryMapper attendanceDailySummaryMapper;
    private final com.company.hrms.attendance.mapper.AttendanceMonthlySummaryMapper attendanceMonthlySummaryMapper;

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onApprovalCompleted(ApprovalCompletedEvent event) {
        String processType = event.getProcessType();
        Long businessId = event.getBusinessId();
        String result = event.getResult();
        log.info("审批事件: processType={}, businessId={}, result={}", processType, businessId, result);

        if (businessId == null) {
            log.warn("审批事件 businessId 为空，跳过");
            return;
        }

        try {
            switch (processType) {
                case "LEAVE" -> handleLeave(businessId, result);
                case "MAKEUP" -> handleMakeup(businessId, result);
                case "OVERTIME" -> handleOvertime(businessId, result);
                default -> log.debug("忽略非考勤审批类型: {}", processType);
            }
        } catch (Exception e) {
            log.error("审批事件处理失败: processType={}, businessId={}", processType, businessId, e);
        }
    }

    /** 请假审批完成 */
    private void handleLeave(Long applicationId, String result) {
        LeaveApplication app = leaveApplicationMapper.selectById(applicationId);
        if (app == null) {
            log.warn("请假申请不存在: id={}", applicationId);
            return;
        }

        if ("APPROVED".equals(result)) {
            app.setStatus("APPROVED");
            leaveApplicationMapper.updateById(app);

            // 更新考勤日汇总：请假期间每天记为 LEAVE
            Long empId = app.getEmployeeId();
            java.time.LocalDate startDate = app.getStartTime().toLocalDate();
            java.time.LocalDate endDate = app.getEndTime().toLocalDate();
            java.math.BigDecimal leaveDays = app.getLeaveDays() != null ? app.getLeaveDays() : java.math.BigDecimal.ZERO;
            long totalDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
            // 按天数均分 leaveDays 到每天
            java.math.BigDecimal perDayLeave = totalDays > 0
                    ? leaveDays.divide(java.math.BigDecimal.valueOf(totalDays), 10, java.math.RoundingMode.HALF_UP)
                    : java.math.BigDecimal.ZERO;

            java.time.LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                AttendanceDailySummary daily = attendanceDailySummaryMapper.selectByEmployeeAndDate(empId, current);
                if (daily == null) {
                    daily = new AttendanceDailySummary();
                    daily.setEmployeeId(empId);
                    daily.setSummaryDate(current);
                    daily.setDayStatus("LEAVE");
                    daily.setLeaveDays(perDayLeave);
                    daily.setOvertimeHours(java.math.BigDecimal.ZERO);
                    attendanceDailySummaryMapper.insert(daily);
                } else {
                    daily.setDayStatus("LEAVE");
                    daily.setLeaveDays(perDayLeave);
                    attendanceDailySummaryMapper.updateById(daily);
                }
                current = current.plusDays(1);
            }

            // 重新聚合月考勤汇总
            String period = startDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            java.time.LocalDate monthStart = startDate.withDayOfMonth(1);
            java.time.LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
            java.util.List<AttendanceDailySummary> dailyList = attendanceDailySummaryMapper.selectByEmployeeAndPeriod(
                    empId, monthStart, monthEnd);
            int shouldAttendDays = 0;
            java.math.BigDecimal totalLeaveDays = java.math.BigDecimal.ZERO;
            for (AttendanceDailySummary ds : dailyList) {
                shouldAttendDays++;
                if ("LEAVE".equals(ds.getDayStatus())) {
                    totalLeaveDays = totalLeaveDays.add(ds.getLeaveDays() != null ? ds.getLeaveDays() : java.math.BigDecimal.ZERO);
                }
            }
            AttendanceMonthlySummary monthly = attendanceMonthlySummaryMapper.selectByEmployeeAndPeriod(empId, period);
            if (monthly != null) {
                monthly.setLeaveDays(totalLeaveDays);
                attendanceMonthlySummaryMapper.updateById(monthly);
            }

            log.info("请假已通过, 日汇总已更新: id={}, empId={}, days={}", applicationId, empId, leaveDays);

        } else if ("REJECTED".equals(result)) {
            app.setStatus("REJECTED");
            leaveApplicationMapper.updateById(app);
            // 恢复预扣余额（年假/调休）
            restoreLeaveBalance(app);
            log.info("请假已驳回, 余额已恢复: id={}", applicationId);
        }
    }

    /** 恢复预扣余额 */
    private void restoreLeaveBalance(LeaveApplication app) {
        String leaveType = app.getLeaveType();
        if (!"ANNUAL".equals(leaveType) && !"COMP_OFF".equals(leaveType)) {
            return;
        }
        LeaveBalance balance = leaveBalanceMapper.selectByEmployeeAndTypeAndYear(
                app.getEmployeeId(), leaveType, LocalDate.now().getYear());
        if (balance != null) {
            balance.setBalance(balance.getBalance().add(app.getLeaveDays()));
            leaveBalanceMapper.updateById(balance);
            log.info("恢复余额: empId={}, type={}, days={}", app.getEmployeeId(), leaveType, app.getLeaveDays());
        }
    }

    /** 补卡审批完成 */
    private void handleMakeup(Long supplementId, String result) {
        AttendanceSupplement sup = attendanceSupplementMapper.selectById(supplementId);
        if (sup == null) {
            log.warn("补卡申请不存在: id={}", supplementId);
            return;
        }

        if ("APPROVED".equals(result)) {
            sup.setStatus("APPROVED");
            attendanceSupplementMapper.updateById(sup);

            LocalDate punchDate = LocalDate.parse(sup.getMakeupDate());
            String punchType = sup.getPunchType();

            // 先删除该员工当天同类型的旧打卡记录（补卡替换旧的，避免重复）
            attendanceRecordMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AttendanceRecord>()
                            .eq(AttendanceRecord::getEmployeeId, sup.getEmployeeId())
                            .eq(AttendanceRecord::getPunchDate, punchDate)
                            .eq(AttendanceRecord::getPunchType, punchType));

            // 写入新的打卡记录（补卡通过后生成）
            AttendanceRecord record = new AttendanceRecord();
            record.setEmployeeId(sup.getEmployeeId());
            record.setPunchDate(punchDate);
            record.setPunchTime(sup.getMakeupTime());
            record.setPunchType(punchType);
            record.setPunchStatus("NORMAL"); // 补卡审批通过，视为正常
            record.setSource("MAKEUP");
            attendanceRecordMapper.insert(record);

            // 更新日汇总：删除旧的当日汇总，重新聚合
            attendanceDailySummaryMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AttendanceDailySummary>()
                            .eq(AttendanceDailySummary::getEmployeeId, sup.getEmployeeId())
                            .eq(AttendanceDailySummary::getSummaryDate, punchDate));

            // 重新查询该员工当天的所有打卡记录（已经清理了重复的），聚合日汇总
            java.util.List<AttendanceRecord> dayRecords = attendanceRecordMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AttendanceRecord>()
                            .eq(AttendanceRecord::getEmployeeId, sup.getEmployeeId())
                            .eq(AttendanceRecord::getPunchDate, punchDate));

            boolean hasIn = dayRecords.stream().anyMatch(r -> "IN".equals(r.getPunchType()));
            boolean hasOut = dayRecords.stream().anyMatch(r -> "OUT".equals(r.getPunchType()));
            String dayStatus = "ABSENT";
            if (hasIn && hasOut) {
                dayStatus = dayRecords.stream()
                        .map(AttendanceRecord::getPunchStatus)
                        .max(java.util.Comparator.comparingInt(s -> {
                            if ("ABSENT_HALF".equals(s)) return 3;
                            if ("LATE".equals(s)) return 2;
                            if ("EARLY_LEAVE".equals(s)) return 2;
                            return 1;
                        }))
                        .orElse("NORMAL");
            } else if (hasIn) {
                dayStatus = "MISSING_OUT";
            } else if (hasOut) {
                dayStatus = "MISSING_IN";
            }

            AttendanceDailySummary summary = new AttendanceDailySummary();
            summary.setEmployeeId(sup.getEmployeeId());
            summary.setSummaryDate(punchDate);
            summary.setDayStatus(dayStatus);
            dayRecords.stream().filter(r -> "IN".equals(r.getPunchType()))
                    .min(java.util.Comparator.comparing(AttendanceRecord::getPunchTime))
                    .ifPresent(r -> summary.setClockInTime(r.getPunchTime()));
            dayRecords.stream().filter(r -> "OUT".equals(r.getPunchType()))
                    .max(java.util.Comparator.comparing(AttendanceRecord::getPunchTime))
                    .ifPresent(r -> summary.setClockOutTime(r.getPunchTime()));
            summary.setLeaveDays(java.math.BigDecimal.ZERO);
            summary.setOvertimeHours(java.math.BigDecimal.ZERO);
            if (summary.getId() == null) {
                attendanceDailySummaryMapper.insert(summary);
            }

            log.info("补卡已通过, 打卡记录已写入: id={}, empId={}, date={}",
                    supplementId, sup.getEmployeeId(), punchDate);
        } else if ("REJECTED".equals(result)) {
            sup.setStatus("REJECTED");
            attendanceSupplementMapper.updateById(sup);
            log.info("补卡已驳回: id={}", supplementId);
        }
    }

    /** 加班审批完成 */
    private void handleOvertime(Long applicationId, String result) {
        OvertimeApplication app = overtimeApplicationMapper.selectById(applicationId);
        if (app == null) {
            log.warn("加班申请不存在: id={}", applicationId);
            return;
        }

        if ("APPROVED".equals(result)) {
            app.setStatus("APPROVED");
            overtimeApplicationMapper.updateById(app);

            // 写入加班台账
            LocalDate overtimeDate = LocalDate.parse(app.getOvertimeDate());
            String period = overtimeDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            OvertimeLedger ledger = new OvertimeLedger();
            ledger.setEmployeeId(app.getEmployeeId());
            ledger.setApplicationId(app.getId());
            ledger.setPeriod(period);
            ledger.setTotalHours(app.getHours());
            ledger.setRateType(calculateRateType(overtimeDate));
            ledger.setLedgerDate(overtimeDate);
            overtimeLedgerMapper.insert(ledger);

            // 更新日汇总：该日加班时长累加
            Long empId = app.getEmployeeId();
            AttendanceDailySummary daily = attendanceDailySummaryMapper.selectByEmployeeAndDate(empId, overtimeDate);
            if (daily != null) {
                java.math.BigDecimal currentOt = daily.getOvertimeHours() != null
                        ? daily.getOvertimeHours() : java.math.BigDecimal.ZERO;
                daily.setOvertimeHours(currentOt.add(app.getHours()));
                attendanceDailySummaryMapper.updateById(daily);
            }

            // 重新聚合月考勤汇总的加班时长
            java.time.LocalDate monthStart = overtimeDate.withDayOfMonth(1);
            java.time.LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
            java.util.List<AttendanceDailySummary> dailyList = attendanceDailySummaryMapper.selectByEmployeeAndPeriod(
                    empId, monthStart, monthEnd);
            java.math.BigDecimal totalOt = java.math.BigDecimal.ZERO;
            for (AttendanceDailySummary ds : dailyList) {
                if (ds.getOvertimeHours() != null) totalOt = totalOt.add(ds.getOvertimeHours());
            }
            AttendanceMonthlySummary monthly = attendanceMonthlySummaryMapper.selectByEmployeeAndPeriod(empId, period);
            if (monthly != null) {
                monthly.setOvertimeHours(totalOt);
                attendanceMonthlySummaryMapper.updateById(monthly);
            }

            log.info("加班已通过, 台账已写入: id={}, hours={}", applicationId, app.getHours());
        } else if ("REJECTED".equals(result)) {
            app.setStatus("REJECTED");
            overtimeApplicationMapper.updateById(app);
            log.info("加班已驳回: id={}", applicationId);
        }
    }

    /** 计算加班倍率（与服务层逻辑一致） */
    private int calculateRateType(LocalDate date) {
        // 简化：工作日1.5、休息日2.0、节假日3.0
        // 此处实际应查 holiday_calendar 和 workday_config
        int dow = date.getDayOfWeek().getValue();
        if (dow >= 6) return 20; // 周末2.0
        return 15; // 工作日1.5
    }
}
