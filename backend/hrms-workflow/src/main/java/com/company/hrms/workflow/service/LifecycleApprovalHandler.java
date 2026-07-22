package com.company.hrms.workflow.service;

/**
 * 审批引擎终态回调接口：由 DbApprovalService.action 在实例完成后调用。
 * 各生命周期 Service（入职/转正/调岗/离职）实现副作用（改业务单状态、员工状态等）。
 * 这样「推进节点」与「业务规则」分离：改审批链不必改四套业务 SQL。
 */
public interface LifecycleApprovalHandler {

    void onApproved(String processType, String businessKey);

    void onRejected(String processType, String businessKey);

    void onWithdrawn(String processType, String businessKey);
}
