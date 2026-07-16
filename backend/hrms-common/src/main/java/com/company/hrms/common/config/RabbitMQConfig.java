package com.company.hrms.common.config;

/**
 * RabbitMQ 占位（审批催办、薪资分片等后续启用）。
 * <p>
 * 启用步骤见 {@code application-dev.yml} / {@code application.yml} 注释：
 * <ol>
 *   <li>取消 {@code spring.rabbitmq.*} 注释</li>
 *   <li>去掉 {@code RabbitAutoConfiguration} exclude</li>
 *   <li>取消 {@code hrms-app/pom.xml} 中 {@code spring-boot-starter-amqp} 注释</li>
 *   <li>在本类增加 {@code Jackson2JsonMessageConverter} Bean</li>
 * </ol>
 */
public final class RabbitMQConfig {

    public static final String HRMS_EXCHANGE = "hrms.exchange";

    private RabbitMQConfig() {
    }
}
