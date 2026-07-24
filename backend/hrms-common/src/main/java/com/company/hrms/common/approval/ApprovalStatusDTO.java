package com.company.hrms.common.approval;

import lombok.Data;

/**
 * 审批实例状态快照：供业务模块展示进度（当前节点文案等）。
 */
@Data
public class ApprovalStatusDTO {

    private Long instanceId;
    private String status;
    private String currentNodeLabel;
}
