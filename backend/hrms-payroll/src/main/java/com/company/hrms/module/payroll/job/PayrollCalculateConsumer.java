package com.company.hrms.module.payroll.job;

import com.company.hrms.module.payroll.service.CalculateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 核算异步消费者。仅 {@code hrms.rabbitmq.enabled=true} 时装配。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "hrms.rabbitmq", name = "enabled", havingValue = "true")
public class PayrollCalculateConsumer {

    private final CalculateService calculateService;

    /**
     * 处理核算消息
     *
     * @param message 消息内容，包含 batchId 和 period
     */
    @RabbitListener(queues = "hrms.payroll.calculate")
    public void handleCalculate(Map<String, Object> message) {
        Long batchId = Long.valueOf(message.get("batchId").toString());
        log.info("收到核算消息: batchId={}", batchId);
        try {
            calculateService.calculate(batchId);
            log.info("异步核算完成: batchId={}", batchId);
        } catch (Exception e) {
            log.error("异步核算失败: batchId={}", batchId, e);
            // 重试由 RabbitMQ 配置的 retry 机制处理
        }
    }
}
