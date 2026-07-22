package com.company.hrms.module.payroll.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 核算事件发布者。
 * 本地排除 AMQP / 无 RabbitTemplate 时降级打日志；核算主路径见 BatchController 同步 calculate。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollEventPublisher {

    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;

    /**
     * 发送异步核算消息
     *
     * @param batchId 批次 ID
     * @param period  核算账期，格式 YYYY-MM
     */
    public void sendCalculate(Long batchId, String period) {
        RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        if (rabbitTemplate == null) {
            log.warn("[MQ] RabbitTemplate 不可用，核算消息降级日志 batchId={} period={}", batchId, period);
            return;
        }
        rabbitTemplate.convertAndSend("hrms.payroll", "payroll.calculate",
                Map.of("batchId", batchId, "period", period));
        log.info("已发送核算消息: batchId={}, period={}", batchId, period);
    }
}
