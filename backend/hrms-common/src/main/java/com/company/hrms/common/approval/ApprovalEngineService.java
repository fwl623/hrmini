package com.company.hrms.common.approval;

import java.util.List;

/**
 * 统一审批引擎跨模块 SPI（实现位于 hrms-workflow，其它模块只依赖本接口）。
 * 用于请假/加班/入转调离等业务创建审批实例、查状态、撤回。
 */
public interface ApprovalEngineService {

    /** 创建审批实例并生成首节点待办 */
    CreateApprovalResult createInstance(CreateApprovalRequest request);

    /** 查询实例当前状态与节点标签 */
    ApprovalStatusDTO getInstanceStatus(Long instanceId);

    /** 发起人撤回（仅允许在规则允许的节点） */
    boolean withdrawInstance(Long instanceId, Long operatorId);

    /**
     * 统计某用户作为当前有效审批人的待办数量。
     * <p>
     * 口径与审批中心 {@code GET /approvals/tasks/stats} 的 {@code pending} 一致
     *（含本人 assignee / actualAssignee，以及生效中的委托）。
     */
    long countPendingTasksForAssignee(long userId);

    /**
     * 列出某用户作为有效审批人的 PENDING 待办（按 id 倒序，最多 {@code limit} 条）。
     * 口径与 {@code GET /approvals/tasks?status=pending} 一致。
     */
    List<PendingApprovalTaskDTO> listPendingTasksForAssignee(long userId, int limit);
}
