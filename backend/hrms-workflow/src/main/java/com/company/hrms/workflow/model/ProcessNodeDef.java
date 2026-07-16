package com.company.hrms.workflow.model;

import lombok.Data;

/**
 * approval_process_def.nodes_json 节点项。
 */
@Data
public class ProcessNodeDef {
    private int order;
    private String label;
    private Long assigneeUserId;
    /** DEPT_MANAGER / NEW_DEPT_MANAGER / HR_STAFF / SUPERVISOR — 开发期由 DevAssignees 解析 */
    private String assigneeType;
    /** true=条件节点，仅 needSecondApproval 时启用 */
    private boolean optional;
    private String condition;
}
