package com.company.hrms.workflow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.employee.service.EmployeeLifecycleService;
import com.company.hrms.workflow.entity.ApprovalInstance;
import com.company.hrms.workflow.entity.ApprovalTask;
import com.company.hrms.workflow.mapper.ApprovalInstanceMapper;
import com.company.hrms.workflow.mapper.ApprovalTaskMapper;
import com.company.hrms.workflow.notify.ApprovalNotifyPublisher;
import com.company.hrms.workflow.service.DelegationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 扫描超时待办：sla_deadline &lt; now 且 overdue=0 → overdue=1，催办审批人并升级知会 HR；顺带清理过期委托。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueCheckJob {

    private final ApprovalTaskMapper taskMapper;
    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalNotifyPublisher notifyPublisher;
    private final DelegationService delegationService;
    private final EmployeeLifecycleService employeeLifecycleService;

    /** 每 15 分钟扫一次 */
    @Scheduled(cron = "0 */15 * * * ?")
    public void markOverdue() {
        int expiredDelegations = delegationService.expireOverdue(LocalDate.now());
        if (expiredDelegations > 0) {
            log.info("OverdueCheckJob expired delegations={}", expiredDelegations);
        }
        int updated = runOnce();
        if (updated > 0) {
            log.info("OverdueCheckJob marked overdue={}", updated);
        }
    }

    /** 供单测 / 手动触发：仅对新标记逾期的 PENDING 任务发催办 + 升级 */
    public int runOnce() {
        LocalDateTime now = LocalDateTime.now();
        List<ApprovalTask> due = taskMapper.selectList(new LambdaQueryWrapper<ApprovalTask>()
                .eq(ApprovalTask::getStatus, "PENDING")
                .and(w -> w.eq(ApprovalTask::getOverdue, 0).or().isNull(ApprovalTask::getOverdue))
                .lt(ApprovalTask::getSlaDeadline, now));
        int updated = 0;
        for (ApprovalTask task : due) {
            task.setOverdue(1);
            taskMapper.updateById(task);
            updated++;
            Long assigneeId = task.getActualAssigneeId() != null ? task.getActualAssigneeId() : task.getAssigneeId();
            if (assigneeId != null) {
                try {
                    notifyPublisher.publishImmediateRemind(task.getId(), assigneeId);
                } catch (Exception e) {
                    log.warn("催办失败 taskId={} assigneeId={}: {}",
                            task.getId(), assigneeId, e.getMessage());
                }
            }
            escalateToHr(task, assigneeId);
        }
        return updated;
    }

    private void escalateToHr(ApprovalTask task, Long assigneeId) {
        try {
            ApprovalInstance instance = instanceMapper.selectById(task.getInstanceId());
            String processType = instance == null ? null : instance.getProcessType();
            Long hrUserId = employeeLifecycleService.resolveHrApproverUserId(assigneeId);
            if (hrUserId != null && (assigneeId == null || !hrUserId.equals(assigneeId))) {
                notifyPublisher.publishOverdueEscalation(
                        task.getId(),
                        assigneeId == null ? 0L : assigneeId,
                        processType,
                        hrUserId);
            }
        } catch (Exception e) {
            log.warn("逾期升级失败 taskId={}: {}", task.getId(), e.getMessage());
        }
    }

    public long countPendingOverdue() {
        return taskMapper.selectCount(new LambdaQueryWrapper<ApprovalTask>()
                .eq(ApprovalTask::getStatus, "PENDING")
                .eq(ApprovalTask::getOverdue, 1));
    }
}
