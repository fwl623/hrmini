package com.company.hrms.module.payroll.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 核算事件发布者。
 * <p>
 * RabbitTemplate 不可用或发送失败时返回 false，由调用方改为同步核算，
 * 避免批次永久卡在 {@code CALCULATING}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollEventPublisher {

    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;

    /**
     * 发送异步核算消息。
     *
     * @return true=已投递 MQ；false=应改走同步核算
     */
    public boolean sendCalculate(Long batchId, String period) {
        RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        if (rabbitTemplate == null) {
            log.warn("[MQ] RabbitTemplate 不可用，将同步核算 batchId={} period={}", batchId, period);
            return false;
        }
        try {
            rabbitTemplate.convertAndSend("hrms.payroll", "payroll.calculate",
                    Map.of("batchId", batchId, "period", period));
            log.info("已发送核算消息: batchId={}, period={}", batchId, period);
            return true;
        } catch (Exception e) {
            log.warn("[MQ] 发送核算消息失败，将同步核算 batchId={}: {}", batchId, e.getMessage());
            return false;
        }
    }
}
