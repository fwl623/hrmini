package com.company.hrms.module.attendance.job;

import com.company.hrms.common.event.EmployeeStatusChangeEvent;
import com.company.hrms.module.attendance.service.LeaveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 员工状态变更事件监听器
 * 入职时初始化年假余额
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmployeeStatusEventListener {

    private final LeaveService leaveService;

    @EventListener
    public void onEmployeeStatusChange(EmployeeStatusChangeEvent event) {
        try {
            // 仅处理入职事件
            if (!"ONBOARDING".equals(event.getTriggerSource())) {
                return;
            }
            Long employeeId = event.getEmployeeId();
            java.time.LocalDate hireDate = event.getEffectDate();
            if (employeeId == null) {
                log.warn("入职事件 employeeId 为空，跳过");
                return;
            }
            leaveService.initAnnualBalance(employeeId, hireDate);
            log.info("入职年假初始化完成: employeeId={}, hireDate={}", employeeId, hireDate);
        } catch (Exception e) {
            log.error("处理入职年假初始化失败", e);
        }
    }
}
