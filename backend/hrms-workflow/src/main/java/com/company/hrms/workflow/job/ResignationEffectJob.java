package com.company.hrms.workflow.job;

import com.company.hrms.workflow.service.ResignationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 离职生效 Job：每天 00:05，将到期待离职 → 已离职。
 * 副作用（禁账号、释放工号、状态事件、MQ 通知）见 effectResign / ResignationService。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResignationEffectJob {

    private final ResignationService resignationService;

    @Scheduled(cron = "0 5 0 * * ?")
    public void effect() {
        int n = resignationService.effectDueResignations(LocalDate.now());
        log.info("ResignationEffectJob done, effected={}", n);
    }
}
