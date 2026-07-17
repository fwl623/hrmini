package com.company.hrms.workflow.enums;

/**
 * 审批操作（对齐 approval_log.action / tasks action API）。
 */
public enum ApprovalAction {
    SUBMIT,
    APPROVE,
    REJECT,
    FORWARD,
    WITHDRAW,
    /** 业务侧「放弃入职」等，区别于审批驳回 REJECT */
    ABANDON
}
