package com.company.hrms.workflow.client;

/**
 * A 组 auth 建号 Feign 约定（入职 confirm 后创建 sys_user）。
 * 默认 Mock；开关关闭后走本地/真实调用。
 */
public interface AuthAccountClient {

    record CreateAccountRequest(String username, Long employeeId, String displayName) {
    }

    record CreateAccountResponse(Long userId) {
    }

    CreateAccountResponse createAccount(CreateAccountRequest request);
}
