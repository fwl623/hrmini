package com.company.hrms.module.payroll.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 核算事件发布者
 * 发送异步核算消息到 MQ
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送异步核算消息
     *
     * @param batchId 批次 ID
     * @param period  核算账期，格式 YYYY-MM
     */
    public void sendCalculate(Long batchId, String period) {
        rabbitTemplate.convertAndSend("hrms.payroll", "payroll.calculate",
                Map.of("batchId", batchId, "period", period));
        log.info("已发送核算消息: batchId={}, period={}", batchId, period);
    }
}
