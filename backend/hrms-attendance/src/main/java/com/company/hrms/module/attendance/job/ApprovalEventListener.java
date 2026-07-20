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
            log.info("补卡已通过: id={}", supplementId);
            // TODO: 补卡通过后重写 attendance_record（需确认补卡时间格式）
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
