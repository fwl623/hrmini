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
    private final com.company.hrms.attendance.mapper.BalanceChangeLogMapper balanceChangeLogMapper;
    private final com.company.hrms.attendance.mapper.AttendanceMonthLockMapper attendanceMonthLockMapper;

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

            // 更新余额变动日志为 CONFIRMED
            try {
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.company.hrms.attendance.entity.BalanceChangeLog> wrapper =
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                wrapper.eq(com.company.hrms.attendance.entity.BalanceChangeLog::getSourceId, applicationId)
                       .eq(com.company.hrms.attendance.entity.BalanceChangeLog::getSourceType, "SUBMIT");
                com.company.hrms.attendance.entity.BalanceChangeLog log = balanceChangeLogMapper.selectOne(wrapper);
                if (log != null) {
                    log.setStatus("CONFIRMED");
                    balanceChangeLogMapper.updateById(log);
                }
            } catch (Exception e) {
                log.warn("更新余额变动日志失败: applicationId={}", applicationId, e);
            }

            // 更新考勤日汇总：请假期间每天记为 LEAVE
            Long empId = app.getEmployeeId();
            java.time.LocalDate startDate = app.getStartTime().toLocalDate();
            java.time.LocalDate endDate = app.getEndTime().toLocalDate();
            java.math.BigDecimal leaveDays = app.getLeaveDays() != null ? app.getLeaveDays() : java.math.BigDecimal.ZERO;
            long totalDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;

            // v2.1 薪资锁定保护：检查考勤月是否锁定
            String period = startDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            com.company.hrms.attendance.entity.AttendanceMonthLock monthLock = attendanceMonthLockMapper.selectByPeriod(period);
            boolean salaryLocked = (monthLock != null && monthLock.getStatus() == 20);
            if (salaryLocked) {
                log.warn("考勤月已锁定，请假通过后无法自动回溯日报: empId={}, period={}", empId, period);
            } else {
                // 按天数均分 leaveDays 到每天
                java.math.BigDecimal perDayLeave = totalDays > 0
                        ? leaveDays.divide(java.math.BigDecimal.valueOf(totalDays), 10, java.math.RoundingMode.HALF_UP)
                        : java.math.BigDecimal.ZERO;

            java.time.LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                AttendanceDailySummary daily = attendanceDailySummaryMapper.selectByEmployeeAndDate(empId, current);
                // v2.1: 判断当天哪些槽位被请假覆盖
                boolean isFirstDay = current.equals(startDate);
                boolean isLastDay = current.equals(endDate);
                // 中间天 → 全天覆盖；首日/末日 → 按时间段判断
                boolean coversAm, coversPm;
                if (!isFirstDay && !isLastDay) {
                    coversAm = true;
                    coversPm = true;
                } else {
                    java.time.LocalTime s = isFirstDay ? app.getStartTime().toLocalTime() : java.time.LocalTime.MIDNIGHT;
                    java.time.LocalTime e = isLastDay ? app.getEndTime().toLocalTime() : java.time.LocalTime.MIDNIGHT.plusHours(23).plusMinutes(59);
                    coversAm = !(e.isBefore(java.time.LocalTime.NOON) || s.isAfter(java.time.LocalTime.NOON));
                    coversPm = !(e.isBefore(java.time.LocalTime.NOON) || s.isAfter(java.time.LocalTime.NOON));
                }

                if (daily == null) {
                    daily = new AttendanceDailySummary();
                    daily.setEmployeeId(empId);
                    daily.setSummaryDate(current);
                    daily.setDayStatus("am:" + (coversAm ? 4 : 5) + ",pm:" + (coversPm ? 4 : 5));
                    daily.setLeaveDays(perDayLeave);
                    daily.setOvertimeHours(java.math.BigDecimal.ZERO);
                    attendanceDailySummaryMapper.insert(daily);
                } else {
                    // 合并：保留已有状态，只将请假覆盖的槽位置为4
                    String raw = daily.getDayStatus();
                    int amCode = 5, pmCode = 5;
                    if (raw != null && raw.startsWith("am:")) {
                        try {
                            String[] parts = raw.split(",");
                            amCode = Integer.parseInt(parts[0].split(":")[1]);
                            pmCode = Integer.parseInt(parts[1].split(":")[1]);
                        } catch (Exception e) {}
                    }
                    if (coversAm) amCode = 4;
                    if (coversPm) pmCode = 4;
                    daily.setDayStatus("am:" + amCode + ",pm:" + pmCode);
                    daily.setLeaveDays(perDayLeave);
                    attendanceDailySummaryMapper.updateById(daily);
                }
                current = current.plusDays(1);
            }

            // 重新聚合月考勤汇总（period 已在上层定义）
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

            } // end else (salary not locked)

            log.info("请假已通过, 日汇总已更新: id={}, empId={}, days={}", applicationId, empId, leaveDays);

        } else if ("REJECTED".equals(result)) {
            app.setStatus("REJECTED");
            leaveApplicationMapper.updateById(app);
            // 恢复预扣余额（年假/调休）
            restoreLeaveBalance(app);
            // 更新余额变动日志为 REFUNDED
            try {
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.company.hrms.attendance.entity.BalanceChangeLog> wrapper =
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                wrapper.eq(com.company.hrms.attendance.entity.BalanceChangeLog::getSourceId, applicationId)
                       .eq(com.company.hrms.attendance.entity.BalanceChangeLog::getSourceType, "SUBMIT");
                com.company.hrms.attendance.entity.BalanceChangeLog log = balanceChangeLogMapper.selectOne(wrapper);
                if (log != null) {
                    log.setStatus("REFUNDED");
                    balanceChangeLogMapper.updateById(log);
                }
            } catch (Exception e) {
                log.warn("更新余额变动日志失败: applicationId={}", applicationId, e);
            }
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

            // v2.1 原子化重算：仅重算被补卡影响的槽位
            Long empId = sup.getEmployeeId();
            AttendanceDailySummary summary = attendanceDailySummaryMapper.selectByEmployeeAndDate(empId, punchDate);

            // 解析现有槽位状态，如果日汇总不存在则默认双缺卡
            int amCode = 5, pmCode = 5;
            if (summary != null && summary.getDayStatus() != null && summary.getDayStatus().startsWith("am:")) {
                try {
                    String[] parts = summary.getDayStatus().split(",");
                    amCode = Integer.parseInt(parts[0].split(":")[1]);
                    pmCode = Integer.parseInt(parts[1].split(":")[1]);
                } catch (Exception e) { /* 解析失败用默认值 */ }
            }

            // 仅重算被补卡的槽位
            java.time.LocalTime workStart = java.time.LocalTime.of(9, 0);
            java.time.LocalTime workEnd = java.time.LocalTime.of(18, 0);
            int lateThreshold = 15;

            if ("IN".equals(punchType)) {
                // 补上班卡 → 仅重算 AM 槽位
                java.time.LocalTime t = sup.getMakeupTime().toLocalTime();
                if (!t.isAfter(workStart)) {
                    amCode = 0;
                } else if (!t.isAfter(workStart.plusMinutes(lateThreshold))) {
                    amCode = 1;
                } else {
                    amCode = 3;
                }
                // PM 保持不变
            } else {
                // 补下班卡 → 仅重算 PM 槽位
                java.time.LocalTime t = sup.getMakeupTime().toLocalTime();
                if (!t.isBefore(workEnd)) {
                    pmCode = 0;
                } else if (!t.isBefore(workEnd.minusMinutes(lateThreshold))) {
                    pmCode = 2;
                } else {
                    pmCode = 3;
                }
                // AM 保持不变
            }

            if (summary == null) {
                summary = new AttendanceDailySummary();
                summary.setEmployeeId(empId);
                summary.setSummaryDate(punchDate);
            }
            summary.setDayStatus("am:" + amCode + ",pm:" + pmCode);
            if ("IN".equals(punchType)) {
                summary.setClockInTime(sup.getMakeupTime());
            } else {
                summary.setClockOutTime(sup.getMakeupTime());
            }
            summary.setLeaveDays(java.math.BigDecimal.ZERO);
            summary.setOvertimeHours(java.math.BigDecimal.ZERO);
            if (summary.getId() == null) {
                attendanceDailySummaryMapper.insert(summary);
            } else {
                attendanceDailySummaryMapper.updateById(summary);
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

            // v2.1: 加班→调休自动转换（1小时=0.125天）
            try {
                java.math.BigDecimal compDays = app.getHours().divide(java.math.BigDecimal.valueOf(8), 3, java.math.RoundingMode.HALF_UP);
                if (compDays.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    // 查找或创建调休余额记录
                    com.company.hrms.attendance.entity.LeaveBalance lb = leaveBalanceMapper.selectByEmployeeAndTypeAndYear(
                            empId, "COMP_OFF", overtimeDate.getYear());
                    java.math.BigDecimal before;
                    if (lb == null) {
                        lb = new com.company.hrms.attendance.entity.LeaveBalance();
                        lb.setEmployeeId(empId);
                        lb.setLeaveType("COMP_OFF");
                        lb.setYear(overtimeDate.getYear());
                        lb.setTotalQuota(compDays);
                        lb.setUsedQuota(java.math.BigDecimal.ZERO);
                        lb.setRemainingQuota(compDays);
                        lb.setBalance(compDays);
                        lb.setEffectiveDate(overtimeDate);
                        lb.setExpireDate(overtimeDate.withDayOfMonth(overtimeDate.lengthOfMonth()).plusMonths(1));
                        lb.setVersion(0);
                        before = java.math.BigDecimal.ZERO;
                        leaveBalanceMapper.insert(lb);
                    } else {
                        before = lb.getRemainingQuota() != null ? lb.getRemainingQuota() : lb.getBalance();
                        java.math.BigDecimal after = before.add(compDays);
                        if (lb.getRemainingQuota() != null) lb.setRemainingQuota(after);
                        lb.setBalance(after);
                        if (lb.getTotalQuota() != null) lb.setTotalQuota(lb.getTotalQuota().add(compDays));
                        lb.setVersion(lb.getVersion() != null ? lb.getVersion() + 1 : 1);
                        leaveBalanceMapper.updateById(lb);
                    }

                    // 写入余额变动日志
                    com.company.hrms.attendance.entity.BalanceChangeLog log = new com.company.hrms.attendance.entity.BalanceChangeLog();
                    log.setEmployeeId(empId);
                    log.setLeaveType("COMP_OFF");
                    log.setChangeAmount(compDays);
                    log.setSourceType("OVERTIME");
                    log.setSourceId(applicationId);
                    log.setBalanceBefore(before);
                    log.setBalanceAfter(lb.getRemainingQuota() != null ? lb.getRemainingQuota() : lb.getBalance());
                    log.setStatus("CONFIRMED");
                    log.setRemark("加班" + app.getHours() + "小时→调休" + compDays + "天");
                    balanceChangeLogMapper.insert(log);
                }
            } catch (Exception e) {
                log.warn("加班→调休转换失败", e);
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
