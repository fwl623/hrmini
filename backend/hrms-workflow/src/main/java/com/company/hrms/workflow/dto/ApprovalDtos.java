package com.company.hrms.workflow.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

public final class ApprovalDtos {
    private ApprovalDtos() {
    }

    @Data
    public static class TaskStatsVO {
        private long pending;
        private long approvedToday;
        private long overdueCount;
    }

    @Data
    public static class TaskListItemVO {
        private Long taskId;
        private Long instanceId;
        private String processType;
        private String title;
        private String applicantName;
        private String applicantDept;
        private String businessNo;
        private String businessSummary;
        private String currentNodeLabel;
        private String createTime;
        private String dueAt;
        private String status;
    }

    @Data
    public static class TaskDetailVO {
        private TaskBriefVO task;
        private InstanceBriefVO instance;
        private Map<String, Object> businessDetail;
        private List<NodeProgressVO> nodes;
        private List<TimelineItemVO> timeline;
        private List<String> actions;
    }

    @Data
    public static class TaskBriefVO {
        private Long id;
        private String status;
        private String currentNodeLabel;
        private String dueAt;
    }

    @Data
    public static class InstanceBriefVO {
        private String processType;
        private String businessNo;
        private String initiator;
        private String createdAt;
        private String status;
    }

    @Data
    public static class TimelineItemVO {
        private String node;
        private String assignee;
        private String action;
        private String comment;
        private String time;
        private String displayText;
    }

    @Data
    public static class ActionRequest {
        private String action;
        private String comment;
        private Long targetUserId;
        /** 正式离职：部门负责人同意时必填，指定工作交接人 */
        private Long handoverEmployeeId;
    }

    @Data
    public static class InstanceListItemVO {
        private Long instanceId;
        private Long taskId;
        private String processType;
        private String title;
        private String applicantName;
        private String applicantDept;
        private String currentNodeLabel;
        private String createTime;
        private String dueAt;
        private String status;
    }

    /** 发起人查看审批进度 */
    @Data
    public static class InstanceDetailVO {
        private Long instanceId;
        private String processType;
        private String title;
        private String status;
        private String currentNodeLabel;
        private String createdAt;
        private List<NodeProgressVO> nodes;
        private List<TimelineItemVO> timeline;
        /** 与任务详情对齐，供「我发起的」展示离职/转正等业务字段 */
        private Map<String, Object> businessDetail;
    }

    @Data
    public static class NodeProgressVO {
        private int order;
        private String label;
        /** pending / current / done / cancelled */
        private String state;
        /** 配置/原审批人展示名 */
        private String assigneeName;
        /** 实际审批人（转交/委托后） */
        private String actualAssigneeName;
        /** 该节点任务状态：pending/approved/rejected/cancelled */
        private String taskStatus;
    }

    @Data
    public static class DelegationFormRequest {
        private Long delegateUserId;
        private java.time.LocalDate startDate;
        private java.time.LocalDate endDate;
        private String reason;
    }

    @Data
    public static class DelegationVO {
        private Long id;
        private Long delegatorId;
        private String delegatorName;
        private Long delegateUserId;
        private String delegateUserName;
        private String startDate;
        private String endDate;
        private String reason;
        private String status;
        private String createdAt;
    }

    /** 正式离职交接人选人（非敏感字段） */
    @Data
    public static class HandoverCandidateVO {
        private Long employeeId;
        private String name;
        private String empNo;
        private String department;
    }
}
