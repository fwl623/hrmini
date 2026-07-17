package com.company.hrms.workflow.config;

/**
 * 审批催办 RabbitMQ 队列约定（骨架）。
 * <p>
 * 启用步骤：
 * <ol>
 *   <li>hrms-app/pom.xml 取消 spring-boot-starter-amqp 注释</li>
 *   <li>application.yml 去掉 RabbitAutoConfiguration exclude</li>
 *   <li>application-dev.yml 配置 spring.rabbitmq.*</li>
 *   <li>设置 hrms.rabbitmq.enabled=true</li>
 *   <li>本包下可再增加 @Configuration 声明 Exchange/Queue/Binding</li>
 * </ol>
 * 未启用时 {@link com.company.hrms.workflow.notify.ApprovalNotifyPublisher} 降级打日志，
 * {@link com.company.hrms.workflow.job.OverdueCheckJob} 仍可标记逾期。
 */
public final class RabbitConfig {

    public static final String EXCHANGE = "hrms.approval.exchange";
    /** 业务消费队列（催办通知） */
    public static final String QUEUE_NOTIFY = "hrms.approval.notify";
    public static final String ROUTING_NOTIFY = "approval.notify";
    /** 延迟缓冲队列：TTL 到期后转发到 notify */
    public static final String QUEUE_DELAY = "hrms.approval.notify.delay";
    public static final String ROUTING_DELAY = "approval.notify.delay";
    /** 默认 SLA 催办延迟：48 小时 */
    public static final long DEFAULT_REMIND_DELAY_MS = 48L * 60 * 60 * 1000;

    private RabbitConfig() {
    }
}
