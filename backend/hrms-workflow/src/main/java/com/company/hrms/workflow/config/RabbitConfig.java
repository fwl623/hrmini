package com.company.hrms.workflow.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 【 RabbitMQ】审批催办延迟队列：TTL 到期后经 DLX 转入 notify 队列（不是 Redis Key 过期）。
 * <p>
 * 启用条件：{@code hrms.rabbitmq.enabled=true}。本地关闭时 {@link com.company.hrms.workflow.notify.ApprovalNotifyPublisher}
 * 降级为打日志，主事务不受影响——演示环境可不依赖本机 RabbitMQ。
 * <p>
 * 命名：交换机 {@code hrms.approval.exchange}；延迟队列 {@code hrms.approval.notify.delay} → 业务队列 {@code hrms.approval.notify}。
 */
@Configuration
@ConditionalOnProperty(prefix = "hrms.rabbitmq", name = "enabled", havingValue = "true")
public class RabbitConfig {

    public static final String EXCHANGE = "hrms.approval.exchange";
    public static final String QUEUE_NOTIFY = "hrms.approval.notify";
    public static final String ROUTING_NOTIFY = "approval.notify";
    public static final String QUEUE_DELAY = "hrms.approval.notify.delay";
    public static final String ROUTING_DELAY = "approval.notify.delay";
    /** 默认 SLA 催办延迟：48 小时 */
    public static final long DEFAULT_REMIND_DELAY_MS = 48L * 60 * 60 * 1000;

    @Bean
    public DirectExchange approvalNotifyExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue approvalNotifyQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFY).build();
    }

    @Bean
    public Binding approvalNotifyBinding(Queue approvalNotifyQueue, DirectExchange approvalNotifyExchange) {
        return BindingBuilder.bind(approvalNotifyQueue).to(approvalNotifyExchange).with(ROUTING_NOTIFY);
    }

    /**
     * 延迟缓冲队列：消息过期后进入 DLX → notify。
     */
    @Bean
    public Queue approvalNotifyDelayQueue() {
        return QueueBuilder.durable(QUEUE_DELAY)
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(ROUTING_NOTIFY)
                .build();
    }

    @Bean
    public Binding approvalNotifyDelayBinding(Queue approvalNotifyDelayQueue, DirectExchange approvalNotifyExchange) {
        return BindingBuilder.bind(approvalNotifyDelayQueue).to(approvalNotifyExchange).with(ROUTING_DELAY);
    }
}
