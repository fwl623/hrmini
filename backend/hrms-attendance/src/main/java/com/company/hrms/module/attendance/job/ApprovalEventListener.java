package com.company.hrms.module.attendance.job;

import com.company.hrms.attendance.entity.LeaveApplication;
import com.company.hrms.attendance.entity.LeaveBalance;
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
            log.info("请假已通过: id={}", applicationId);
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

            // 写入打卡记录（补卡通过后生成一条新的打卡流水）
            LocalDate punchDate = LocalDate.parse(sup.getMakeupDate());
            AttendanceRecord record = new AttendanceRecord();
            record.setEmployeeId(sup.getEmployeeId());
            record.setPunchDate(punchDate);
            record.setPunchTime(sup.getMakeupTime());
            record.setPunchType(sup.getPunchType());
            record.setPunchStatus("NORMAL"); // 补卡审批通过，视为正常
            record.setSource("MAKEUP");
            attendanceRecordMapper.insert(record);

            // 更新日汇总：删除旧的当日汇总，重新聚合
            attendanceDailySummaryMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AttendanceDailySummary>()
                            .eq(AttendanceDailySummary::getEmployeeId, sup.getEmployeeId())
                            .eq(AttendanceDailySummary::getSummaryDate, punchDate));

            // 重新查询该员工当天的所有打卡记录，聚合日汇总
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
            OvertimeLedger ledger = new OvertimeLedger();
            ledger.setEmployeeId(app.getEmployeeId());
            ledger.setApplicationId(app.getId());
            ledger.setPeriod(overtimeDate.format(DateTimeFormatter.ofPattern("yyyy-MM")));
            ledger.setTotalHours(app.getHours());
            ledger.setRateType(calculateRateType(overtimeDate));
            ledger.setLedgerDate(overtimeDate);
            overtimeLedgerMapper.insert(ledger);

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
