package com.company.hrms.common.approval;

import lombok.Data;

/**
 * 跨模块只读待办摘要（工作台 / AI 办事卡片等），避免依赖 workflow DTO。
 */
@Data
public class PendingApprovalTaskDTO {
    private Long taskId;
    private Long instanceId;
    private String processType;
    private String title;
    private String applicantName;
    private String currentNodeLabel;
    private String createTime;
    private String businessSummary;
}
