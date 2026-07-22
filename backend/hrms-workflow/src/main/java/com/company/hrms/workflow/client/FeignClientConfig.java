package com.company.hrms.workflow.client;

import com.company.hrms.employee.dto.OnboardingArchiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 【跨模块 Client 开关】模拟 Feign 边界。
 * <ul>
 *   <li>{@code hrms.feign.mock-enabled=true}：Mock 建档返回假 employeeId，审批链路可单模块推进</li>
 *   <li>默认 false：本地真实 {@link EmployeeArchiveClient} 调 B 组建档</li>
 * </ul>
 * 讲解时强调：前端感知的是接口成功与否，不直接开关 Feign。
 */
@Configuration
public class FeignClientConfig {

    private static final Logger log = LoggerFactory.getLogger(FeignClientConfig.class);

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "hrms.feign", name = "mock-enabled", havingValue = "true", matchIfMissing = false)
    public EmployeeArchiveClient mockEmployeeArchiveClient() {
        AtomicLong seq = new AtomicLong(900_000);
        return command -> {
            long id = seq.incrementAndGet();
            log.info("[Feign-Mock] EmployeeArchiveClient.archive appId={} -> employeeId={}",
                    command.getApplicationId(), id);
            return id;
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "hrms.feign", name = "mock-enabled", havingValue = "false", matchIfMissing = true)
    public EmployeeArchiveClient localEmployeeArchiveClient(
            com.company.hrms.employee.service.OnboardingService employeeOnboardingService) {
        return command -> {
            log.info("[Feign-Local] EmployeeArchiveClient.archive appId={}", command.getApplicationId());
            return employeeOnboardingService.confirm(command);
        };
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "hrms.feign", name = "mock-enabled", havingValue = "true", matchIfMissing = false)
    public AuthAccountClient mockAuthAccountClient() {
        AtomicLong seq = new AtomicLong(800_000);
        return request -> {
            long userId = seq.incrementAndGet();
            log.info("[Feign-Mock] AuthAccountClient.createAccount username={} employeeId={} -> userId={}",
                    request.username(), request.employeeId(), userId);
            return new AuthAccountClient.CreateAccountResponse(userId);
        };
    }

    /**
     * 真实模式：建号已在 B 组 archive 内完成（InternalUserService），此处仅记日志对齐 Feign 调用点。
     */
    @Bean
    @ConditionalOnProperty(prefix = "hrms.feign", name = "mock-enabled", havingValue = "false", matchIfMissing = true)
    public AuthAccountClient localAuthAccountClient() {
        return request -> {
            log.info("[Feign-Local] AuthAccountClient.createAccount skipped (done in archive), username={} employeeId={}",
                    request.username(), request.employeeId());
            return new AuthAccountClient.CreateAccountResponse(request.employeeId());
        };
    }
}
