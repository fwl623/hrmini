package com.company.hrms.workflow.job;

import com.company.hrms.workflow.service.RegularizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日扫描试用期员工：试用结束日 ≤ 今天+7（含逾期）→ 知会 HR 准备转正评估。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegularizationPendingScanJob {

    private final RegularizationService regularizationService;

    /** 每天 09:00 */
    @Scheduled(cron = "0 0 9 * * ?")
    public void scan() {
        int n = regularizationService.scanAndRemindHr();
        if (n > 0) {
            log.info("RegularizationPendingScanJob reminded HR, pending={}", n);
        }
    }
}
