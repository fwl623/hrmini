package com.company.hrms.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 队列、交换机及消息转换器配置。
 * <p>
 * 本地默认 {@code hrms.rabbitmq.enabled=false}，本类不装配，避免无 Broker 时刷屏。
 * 启用步骤（{@code application.yml}）：
 * <ol>
 *   <li>去掉 {@code RabbitAutoConfiguration} 的 exclude</li>
 *   <li>{@code hrms.rabbitmq.enabled=true}</li>
 *   <li>配置 {@code spring.rabbitmq.host/port/username/password}</li>
 * </ol>
 */
@Configuration
@EnableRabbit
@ConditionalOnProperty(prefix = "hrms.rabbitmq", name = "enabled", havingValue = "true")
public class RabbitMQConfig {

    // ==================== 薪资核算 (Direct Exchange) ====================

    /** 薪资核算 Direct 交换机 */
    public static final String PAYROLL_EXCHANGE = "hrms.payroll";
    /** 核算任务队列 */
    public static final String PAYROLL_CALCULATE_QUEUE = "hrms.payroll.calculate";
    /** 核算任务 routing key */
    public static final String PAYROLL_CALCULATE_KEY = "payroll.calculate";
    /** 核算进度队列 */
    public static final String PAYROLL_PROGRESS_QUEUE = "hrms.payroll.progress";
    /** 核算进度 routing key */
    public static final String PAYROLL_PROGRESS_KEY = "payroll.progress";

    // ==================== 审批事件 (Topic Exchange) ====================

    /** 审批事件 Topic 交换机 */
    public static final String APPROVAL_EXCHANGE = "hrms.approval";
    /** 考勤审批队列 */
    public static final String APPROVAL_ATTENDANCE_QUEUE = "hrms.approval.attendance";
    /** 考勤审批 routing key（匹配 hrms.approval.attendance.*） */
    public static final String APPROVAL_ATTENDANCE_KEY = "hrms.approval.attendance.#";
    /** 薪资审批队列 */
    public static final String APPROVAL_PAYROLL_QUEUE = "hrms.approval.payroll";
    /** 薪资审批 routing key（匹配 hrms.approval.payroll.*） */
    public static final String APPROVAL_PAYROLL_KEY = "hrms.approval.payroll.#";

    // ==================== 消息转换器 ====================

    /**
     * Jackson2Json 消息转换器，使 RabbitTemplate 自动将 Java 对象序列化为 JSON。
     * 消费者端也由相同的 converter 反序列化。
     */
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ==================== 薪资核算交换机与队列绑定 ====================

    @Bean
    public DirectExchange payrollExchange() {
        return new DirectExchange(PAYROLL_EXCHANGE);
    }

    @Bean
    public Queue payrollCalculateQueue() {
        return new Queue(PAYROLL_CALCULATE_QUEUE, true);
    }

    @Bean
    public Binding payrollCalculateBinding() {
        return BindingBuilder
                .bind(payrollCalculateQueue())
                .to(payrollExchange())
                .with(PAYROLL_CALCULATE_KEY);
    }

    @Bean
    public Queue payrollProgressQueue() {
        return new Queue(PAYROLL_PROGRESS_QUEUE, true);
    }

    @Bean
    public Binding payrollProgressBinding() {
        return BindingBuilder
                .bind(payrollProgressQueue())
                .to(payrollExchange())
                .with(PAYROLL_PROGRESS_KEY);
    }

    // ==================== 审批事件交换机与队列绑定 ====================

    @Bean
    public TopicExchange approvalExchange() {
        return new TopicExchange(APPROVAL_EXCHANGE);
    }

    @Bean
    public Queue approvalAttendanceQueue() {
        return new Queue(APPROVAL_ATTENDANCE_QUEUE, true);
    }

    @Bean
    public Binding approvalAttendanceBinding() {
        return BindingBuilder
                .bind(approvalAttendanceQueue())
                .to(approvalExchange())
                .with(APPROVAL_ATTENDANCE_KEY);
    }

    @Bean
    public Queue approvalPayrollQueue() {
        return new Queue(APPROVAL_PAYROLL_QUEUE, true);
    }

    @Bean
    public Binding approvalPayrollBinding() {
        return BindingBuilder
                .bind(approvalPayrollQueue())
                .to(approvalExchange())
                .with(APPROVAL_PAYROLL_KEY);
    }
}
