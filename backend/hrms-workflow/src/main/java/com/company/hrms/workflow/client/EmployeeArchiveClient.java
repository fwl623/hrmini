package com.company.hrms.workflow.client;

import com.company.hrms.employee.dto.OnboardingArchiveCommand;

/**
 * B 组员工建档 Feign 约定（入职 confirm）。
 * 默认 Mock；{@code hrms.feign.mock-enabled=false} 时走本地真实实现。
 */
public interface EmployeeArchiveClient {

    /**
     * @return 新建员工 employeeId
     */
    Long archive(OnboardingArchiveCommand command);
}
