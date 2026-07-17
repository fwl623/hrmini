package com.company.hrms.module.attendance.job;

import com.company.hrms.module.attendance.service.SummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 日终考勤汇总 Job
 *
 * 每日凌晨 02:00 执行，将当天打卡记录聚合为日考勤汇总，再聚合至月考勤汇总。
 * 支持手动触发：SummaryService.runDailySummary(date)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceSummaryJob {

    private final SummaryService summaryService;

    /**
     * 每日 02:00 执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void runDailySummary() {
        log.info("开始执行日终汇总 Job...");
        try {
            summaryService.runDailySummary(null);
            log.info("日终汇总 Job 执行完成");
        } catch (Exception e) {
            log.error("日终汇总 Job 执行失败", e);
        }
    }
}
