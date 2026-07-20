package com.company.hrms.workflow.job;

import com.company.hrms.workflow.service.TransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 调岗生效 Job：每天 00:10，将到期待生效调岗 → 组织/薪资变更。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferEffectJob {

    private final TransferService transferService;

    @Scheduled(cron = "0 10 0 * * ?")
    public void effect() {
        int n = transferService.effectDueTransfers(LocalDate.now());
        log.info("TransferEffectJob done, effected={}", n);
    }
}
