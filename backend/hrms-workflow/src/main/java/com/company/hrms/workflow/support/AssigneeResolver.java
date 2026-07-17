package com.company.hrms.workflow.support;

import com.company.hrms.workflow.model.ProcessNodeDef;

import java.util.List;
import java.util.Map;

/**
 * 审批人解析器（占位）：当前由 {@link DevAssignees} 写死，后续接部门负责人/角色查询。
 */
public interface AssigneeResolver {

    long resolve(ProcessNodeDef node, Map<String, Object> variables);

    /** 开发期默认实现 */
    class DevAssigneeResolver implements AssigneeResolver {
        @Override
        public long resolve(ProcessNodeDef node, Map<String, Object> variables) {
            String type = node.getAssigneeType() == null ? "" : node.getAssigneeType();
            return switch (type) {
                case "NEW_DEPT_MANAGER" -> DevAssignees.NEW_DEPT_MANAGER;
                case "ROLE", "HR_STAFF" -> DevAssignees.HR_STAFF;
                case "SUPERVISOR", "DEPT_MANAGER" -> DevAssignees.DEPT_MANAGER;
                default -> node.getAssigneeUserId() != null ? node.getAssigneeUserId() : DevAssignees.DEPT_MANAGER;
            };
        }
    }

    static List<ProcessNodeDef> regularizationNodes() {
        return List.of(
                node(1, "部门负责人审批", "DEPT_MANAGER"),
                node(2, "HR 审批", "HR_STAFF")
        );
    }

    static List<ProcessNodeDef> transferNodes() {
        return List.of(
                node(1, "原部门确认", "DEPT_MANAGER"),
                node(2, "新部门接收", "NEW_DEPT_MANAGER"),
                node(3, "HR 备案", "HR_STAFF")
        );
    }

    static List<ProcessNodeDef> resignationRequestNodes() {
        return List.of(
                node(1, "直接上级审批", "SUPERVISOR"),
                node(2, "HR 审批", "HR_STAFF")
        );
    }

    static List<ProcessNodeDef> resignationNodes() {
        return List.of(
                node(1, "部门负责人审批", "DEPT_MANAGER"),
                node(2, "HR 审批", "HR_STAFF")
        );
    }

    private static ProcessNodeDef node(int order, String label, String assigneeType) {
        ProcessNodeDef n = new ProcessNodeDef();
        n.setOrder(order);
        n.setLabel(label);
        n.setAssigneeType(assigneeType);
        return n;
    }
}
