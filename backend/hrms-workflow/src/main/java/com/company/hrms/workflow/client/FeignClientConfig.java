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
 * Feign 客户端开关：默认 Mock；{@code hrms.feign.mock-enabled=false} 走本地真实建档。
 */
@Configuration
public class FeignClientConfig {

    private static final Logger log = LoggerFactory.getLogger(FeignClientConfig.class);

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "hrms.feign", name = "mock-enabled", havingValue = "true", matchIfMissing = true)
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
    @ConditionalOnProperty(prefix = "hrms.feign", name = "mock-enabled", havingValue = "false")
    public EmployeeArchiveClient localEmployeeArchiveClient(
            com.company.hrms.employee.service.OnboardingService employeeOnboardingService) {
        return command -> {
            log.info("[Feign-Local] EmployeeArchiveClient.archive appId={}", command.getApplicationId());
            return employeeOnboardingService.confirm(command);
        };
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "hrms.feign", name = "mock-enabled", havingValue = "true", matchIfMissing = true)
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
    @ConditionalOnProperty(prefix = "hrms.feign", name = "mock-enabled", havingValue = "false")
    public AuthAccountClient localAuthAccountClient() {
        return request -> {
            log.info("[Feign-Local] AuthAccountClient.createAccount skipped (done in archive), username={} employeeId={}",
                    request.username(), request.employeeId());
            return new AuthAccountClient.CreateAccountResponse(request.employeeId());
        };
    }
}
