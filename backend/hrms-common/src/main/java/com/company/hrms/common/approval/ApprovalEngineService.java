package com.company.hrms.common.approval;

/**
 * 统一审批引擎跨模块 SPI（实现位于 hrms-workflow）。
 */
public interface ApprovalEngineService {

    CreateApprovalResult createInstance(CreateApprovalRequest request);

    ApprovalStatusDTO getInstanceStatus(Long instanceId);

    boolean withdrawInstance(Long instanceId, Long operatorId);
}
