package com.company.hrms.module.ai.capability;

import com.company.hrms.common.approval.PendingApprovalTaskDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * 审批待办 TASK_LIST 卡片组装。
 */
public final class ApprovalTodoActionFactory {

    public static final String FORM_ID = "approval_todo_list";
    public static final String ACTION_TYPE = "TASK_LIST";
    public static final String INTENT = "approval_todo";

    private ApprovalTodoActionFactory() {
    }

    public static ObjectNode buildAction(ObjectMapper om, List<PendingApprovalTaskDTO> tasks) {
        ObjectNode action = om.createObjectNode();
        action.put("type", ACTION_TYPE);
        action.put("intent", INTENT);
        action.put("label", "我的待审批");
        action.put("formId", FORM_ID);
        action.put("route", "/admin/approval");
        ArrayNode arr = action.putArray("tasks");
        if (tasks != null) {
            for (PendingApprovalTaskDTO t : tasks) {
                if (t == null || t.getTaskId() == null) {
                    continue;
                }
                ObjectNode row = arr.addObject();
                row.put("taskId", t.getTaskId());
                if (t.getInstanceId() != null) {
                    row.put("instanceId", t.getInstanceId());
                }
                if (t.getProcessType() != null) {
                    row.put("processType", t.getProcessType());
                }
                if (t.getTitle() != null) {
                    row.put("title", t.getTitle());
                }
                if (t.getApplicantName() != null) {
                    row.put("applicantName", t.getApplicantName());
                }
                if (t.getCurrentNodeLabel() != null) {
                    row.put("currentNodeLabel", t.getCurrentNodeLabel());
                }
                if (t.getCreateTime() != null) {
                    row.put("createTime", t.getCreateTime());
                }
                if (t.getBusinessSummary() != null) {
                    row.put("businessSummary", t.getBusinessSummary());
                }
            }
        }
        return action;
    }
}
