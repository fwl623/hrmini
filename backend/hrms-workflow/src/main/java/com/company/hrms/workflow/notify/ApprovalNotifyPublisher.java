package com.company.hrms.workflow.notify;

import com.company.hrms.workflow.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 审批催办 / 离职生效 / 审批完成 MQ 发布。
 * {@code hrms.rabbitmq.enabled=false} 时降级为日志；启用后走 RabbitTemplate（含 48h 延迟催办）。
 */
@Component
public class ApprovalNotifyPublisher {

    private static final Logger log = LoggerFactory.getLogger(ApprovalNotifyPublisher.class);

    private final boolean rabbitEnabled;
    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;

    public ApprovalNotifyPublisher(
            @Value("${hrms.rabbitmq.enabled:false}") boolean rabbitEnabled,
            ObjectProvider<RabbitTemplate> rabbitTemplateProvider) {
        this.rabbitEnabled = rabbitEnabled;
        this.rabbitTemplateProvider = rabbitTemplateProvider;
    }

    public void scheduleRemind(long taskId, long assigneeId, String processType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "APPROVAL_REMIND");
        payload.put("eventType", "APPROVAL_REMIND");
        payload.put("taskId", taskId);
        payload.put("assigneeId", assigneeId);
        payload.put("processType", processType);
        payload.put("delayHours", 48);
        payload.put("scheduledAt", LocalDateTime.now().toString());
        publishDelay(RabbitConfig.ROUTING_DELAY, payload, RabbitConfig.DEFAULT_REMIND_DELAY_MS);
    }

    public void publishImmediateRemind(long taskId, long assigneeId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "APPROVAL_REMIND_NOW");
        payload.put("eventType", "APPROVAL_REMIND_NOW");
        payload.put("taskId", taskId);
        payload.put("assigneeId", assigneeId);
        payload.put("remindedAt", LocalDateTime.now().toString());
        publish(RabbitConfig.ROUTING_NOTIFY, payload);
    }

    /** 离职生效 → 通知考勤（字段对齐跨模块契约，消费方由 D 实现） */
    public void publishResignationEffected(long employeeId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "RESIGNATION_EFFECTED");
        payload.put("eventType", "EMPLOYEE_STATUS_CHANGED");
        payload.put("employeeId", employeeId);
        payload.put("oldStatus", "30");
        payload.put("newStatus", "40");
        payload.put("effectDate", LocalDate.now().toString());
        payload.put("triggerSource", "RESIGNATION_EFFECT");
        publish("attendance.resignation.effected", payload);
    }

    /** 审批完成 */
    public void publishApprovalCompleted(String processType, Long instanceId, Long businessId, String result) {
        publishApprovalCompleted(processType, instanceId, businessId, result, null, null);
    }

    public void publishApprovalCompleted(String processType, Long instanceId, Long businessId,
                                         String result, Long applicantId, String comment) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "APPROVAL_COMPLETED");
        payload.put("processType", processType);
        payload.put("instanceId", instanceId);
        payload.put("businessId", businessId);
        payload.put("result", result);
        payload.put("applicantId", applicantId);
        payload.put("comment", comment);
        payload.put("completedAt", LocalDateTime.now().toString());
        publish("hrms.approval.notify", payload);
    }

    private void publishDelay(String routingKey, Map<String, Object> payload, long delayMs) {
        if (!rabbitEnabled) {
            log.info("[MQ-fallback] routing={} delayMs={} payload={}", routingKey, delayMs, payload);
            return;
        }
        RabbitTemplate template = rabbitTemplateProvider.getIfAvailable();
        if (template == null) {
            log.warn("[MQ] RabbitTemplate 不可用，降级日志 routing={} payload={}", routingKey, payload);
            return;
        }
        try {
            template.convertAndSend(RabbitConfig.EXCHANGE, routingKey, payload, message -> {
                message.getMessageProperties().setExpiration(String.valueOf(delayMs));
                return message;
            });
            log.info("[MQ] delayed send ok routing={} delayMs={} payload={}", routingKey, delayMs, payload);
        } catch (Exception e) {
            log.warn("[MQ] delayed send failed routing={}: {}", routingKey, e.getMessage());
        }
    }

    private void publish(String routingKey, Map<String, Object> payload) {
        if (!rabbitEnabled) {
            log.info("[MQ-fallback] routing={} payload={}", routingKey, payload);
            return;
        }
        RabbitTemplate template = rabbitTemplateProvider.getIfAvailable();
        if (template == null) {
            log.warn("[MQ] RabbitTemplate 不可用，降级日志 routing={} payload={}", routingKey, payload);
            return;
        }
        try {
            template.convertAndSend(RabbitConfig.EXCHANGE, routingKey, payload);
            log.info("[MQ] send ok routing={} payload={}", routingKey, payload);
        } catch (Exception e) {
            log.warn("[MQ] send failed routing={}: {}", routingKey, e.getMessage());
        }
    }
}
