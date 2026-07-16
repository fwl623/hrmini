package com.company.hrms.workflow.service;

/**
 * 落库审批终态回调：由各生命周期 Service 实现副作用。
 */
public interface LifecycleApprovalHandler {

    void onApproved(String processType, String businessKey);

    void onRejected(String processType, String businessKey);

    void onWithdrawn(String processType, String businessKey);
}
