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
    /** true=条件节点，仅 needSecondApproval 时启用 */
    private boolean optional;
    private String condition;
}
