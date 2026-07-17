package com.company.hrms.workflow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.hrms.workflow.entity.ApprovalTask;
import com.company.hrms.workflow.mapper.ApprovalTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 扫描超时待办：sla_deadline &lt; now 且 overdue=0 → overdue=1。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueCheckJob {

    private final ApprovalTaskMapper taskMapper;

    /** 每 15 分钟扫一次 */
    @Scheduled(cron = "0 */15 * * * ?")
    public void markOverdue() {
        LocalDateTime now = LocalDateTime.now();
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<ApprovalTask>()
                .set(ApprovalTask::getOverdue, 1)
                .eq(ApprovalTask::getStatus, "PENDING")
                .eq(ApprovalTask::getOverdue, 0)
                .lt(ApprovalTask::getSlaDeadline, now));
        if (updated > 0) {
            log.info("OverdueCheckJob marked overdue={}", updated);
        }
    }

    /** 供单测 / 手动触发 */
    public int runOnce() {
        LocalDateTime now = LocalDateTime.now();
        return taskMapper.update(null, new LambdaUpdateWrapper<ApprovalTask>()
                .set(ApprovalTask::getOverdue, 1)
                .eq(ApprovalTask::getStatus, "PENDING")
                .and(w -> w.eq(ApprovalTask::getOverdue, 0).or().isNull(ApprovalTask::getOverdue))
                .lt(ApprovalTask::getSlaDeadline, now));
    }

    public long countPendingOverdue() {
        return taskMapper.selectCount(new LambdaQueryWrapper<ApprovalTask>()
                .eq(ApprovalTask::getStatus, "PENDING")
                .eq(ApprovalTask::getOverdue, 1));
    }
}
