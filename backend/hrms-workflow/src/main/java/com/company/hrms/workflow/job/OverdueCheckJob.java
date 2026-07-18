package com.company.hrms.workflow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.workflow.entity.ApprovalTask;
import com.company.hrms.workflow.mapper.ApprovalTaskMapper;
import com.company.hrms.workflow.notify.ApprovalNotifyPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 扫描超时待办：sla_deadline &lt; now 且 overdue=0 → overdue=1，并对新逾期任务催办（MQ 关闭时为日志）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueCheckJob {

    private final ApprovalTaskMapper taskMapper;
    private final ApprovalNotifyPublisher notifyPublisher;

    /** 每 15 分钟扫一次 */
    @Scheduled(cron = "0 */15 * * * ?")
    public void markOverdue() {
        int updated = runOnce();
        if (updated > 0) {
            log.info("OverdueCheckJob marked overdue={}", updated);
        }
    }

    /** 供单测 / 手动触发：仅对新标记逾期的 PENDING 任务发催办 */
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
            if (task.getAssigneeId() != null) {
                try {
                    notifyPublisher.publishImmediateRemind(task.getId(), task.getAssigneeId());
                } catch (Exception e) {
                    log.warn("催办失败 taskId={} assigneeId={}: {}",
                            task.getId(), task.getAssigneeId(), e.getMessage());
                }
            }
        }
        return updated;
    }

    public long countPendingOverdue() {
        return taskMapper.selectCount(new LambdaQueryWrapper<ApprovalTask>()
                .eq(ApprovalTask::getStatus, "PENDING")
                .eq(ApprovalTask::getOverdue, 1));
    }
}
