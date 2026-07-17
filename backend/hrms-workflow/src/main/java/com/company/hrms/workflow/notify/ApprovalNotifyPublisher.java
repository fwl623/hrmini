package com.company.hrms.workflow.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 审批催办 / 离职生效 MQ 发布骨架。
 * 未启用 RabbitMQ 时降级为日志，不阻塞启动。
 */
@Component
public class ApprovalNotifyPublisher {

    private static final Logger log = LoggerFactory.getLogger(ApprovalNotifyPublisher.class);

    private final boolean rabbitEnabled;

    public ApprovalNotifyPublisher(
            @Value("${hrms.rabbitmq.enabled:false}") boolean rabbitEnabled) {
        this.rabbitEnabled = rabbitEnabled;
    }

    public void scheduleRemind(long taskId, long assigneeId, String processType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "APPROVAL_REMIND");
        payload.put("taskId", taskId);
        payload.put("assigneeId", assigneeId);
        payload.put("processType", processType);
        payload.put("delayHours", 48);
        publish("approval.notify.delay", payload);
    }

    public void publishImmediateRemind(long taskId, long assigneeId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "APPROVAL_REMIND_NOW");
        payload.put("taskId", taskId);
        payload.put("assigneeId", assigneeId);
        publish("approval.notify", payload);
    }

    /** 离职生效 → 通知 D 组考勤（骨架） */
    public void publishResignationEffected(long employeeId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "RESIGNATION_EFFECTED");
        payload.put("employeeId", employeeId);
        publish("attendance.resignation.effected", payload);
    }

    private void publish(String routingKey, Map<String, Object> payload) {
        if (!rabbitEnabled) {
            log.info("[MQ-fallback] routing={} payload={}", routingKey, payload);
            return;
        }
        // Day5+：注入 RabbitTemplate 后 convertAndSend(RabbitConfig.EXCHANGE, routingKey, payload)
        log.info("[MQ-enabled-stub] routing={} payload={} (RabbitTemplate 待接入)", routingKey, payload);
    }
}
