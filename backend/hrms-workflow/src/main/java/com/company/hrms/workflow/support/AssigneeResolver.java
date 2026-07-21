package com.company.hrms.workflow.support;

import com.company.hrms.workflow.model.ProcessNodeDef;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 审批人解析器：优先使用节点上已写入的真实 userId；
 * 考勤请假等经 {@link com.company.hrms.workflow.service.DbApprovalService} 创建时会先 enrich 真实审批人。
 * 未写入时回退 {@link DevAssignees} 开发桩。
 */
public interface AssigneeResolver {

    long resolve(ProcessNodeDef node, Map<String, Object> variables);

    /**
     * 默认解析：优先用节点上已写入的真实 userId（业务侧解析部门负责人/HR），
     * 否则回退 DevAssignees 开发桩。
     */
    class DevAssigneeResolver implements AssigneeResolver {
        @Override
        public long resolve(ProcessNodeDef node, Map<String, Object> variables) {
            if (node != null && node.getAssigneeUserId() != null) {
                return node.getAssigneeUserId();
            }
            String type = node == null || node.getAssigneeType() == null ? "" : node.getAssigneeType();
            return switch (type) {
                case "NEW_DEPT_MANAGER" -> DevAssignees.NEW_DEPT_MANAGER;
                case "FINANCE", "FINANCE_MANAGER" -> DevAssignees.FINANCE;
                case "ROLE", "HR_STAFF" -> DevAssignees.HR_STAFF;
                case "SUPERVISOR", "DEPT_MANAGER" -> DevAssignees.DEPT_MANAGER;
                default -> DevAssignees.DEPT_MANAGER;
            };
        }
    }

    static List<ProcessNodeDef> resolveNodes(String processType, Map<String, Object> variables) {
        Map<String, Object> vars = variables == null ? Map.of() : variables;
        return switch (processType == null ? "" : processType.toUpperCase()) {
            case "ONBOARDING" -> onboardingNodes(vars);
            case "REGULARIZATION" -> regularizationNodes();
            case "TRANSFER" -> transferNodes();
            case "RESIGNATION_REQUEST" -> resignationRequestNodes();
            case "RESIGNATION" -> resignationNodes();
            case "LEAVE" -> leaveNodes();
            case "MAKEUP" -> makeupNodes();
            case "OVERTIME" -> overtimeNodes(vars);
            case "PAYROLL_BATCH" -> payrollBatchNodes();
            case "MOBILE_CHANGE" -> mobileChangeNodes();
            default -> List.of(node(1, "默认审批", "HR_STAFF"));
        };
    }

    static List<ProcessNodeDef> onboardingNodes(Map<String, Object> vars) {
        List<ProcessNodeDef> nodes = new ArrayList<>();
        nodes.add(node(1, "部门负责人审批", "DEPT_MANAGER"));
        boolean needSecond = Boolean.TRUE.equals(vars.get("needSecondApproval"));
        if (needSecond) {
            nodes.add(node(2, "HR 二审", "HR_STAFF"));
        }
        return nodes;
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

    /** 员工离职申请：默认 HR 单节点（业务侧应写入真实 assigneeUserId） */
    static List<ProcessNodeDef> resignationRequestNodes() {
        return List.of(node(1, "HR 审批", "HR_STAFF"));
    }

    static List<ProcessNodeDef> resignationNodes() {
        return List.of(
                node(1, "部门负责人审批", "DEPT_MANAGER"),
                node(2, "HR 审批", "HR_STAFF")
        );
    }

    static List<ProcessNodeDef> leaveNodes() {
        return List.of(
                node(1, "直接上级审批", "SUPERVISOR"),
                node(2, "HR 审批", "HR_STAFF")
        );
    }

    static List<ProcessNodeDef> makeupNodes() {
        return List.of(node(1, "直接上级审批", "SUPERVISOR"));
    }

    static List<ProcessNodeDef> overtimeNodes(Map<String, Object> vars) {
        List<ProcessNodeDef> nodes = new ArrayList<>();
        nodes.add(node(1, "直接上级审批", "SUPERVISOR"));
        Object hoursObj = vars.get("dailyTotalHours");
        BigDecimal hours = toDecimal(hoursObj);
        if (hours != null && hours.compareTo(BigDecimal.valueOf(4)) >= 0) {
            nodes.add(node(2, "HR 二审", "HR_STAFF"));
        }
        return nodes;
    }

    static List<ProcessNodeDef> payrollBatchNodes() {
        return List.of(
                node(1, "财务审批", "FINANCE"),
                node(2, "HR 备案", "HR_STAFF")
        );
    }

    static List<ProcessNodeDef> mobileChangeNodes() {
        return List.of(node(1, "HR 审批", "HR_STAFF"));
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(v.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static ProcessNodeDef node(int order, String label, String assigneeType) {
        ProcessNodeDef n = new ProcessNodeDef();
        n.setOrder(order);
        n.setLabel(label);
        n.setAssigneeType(assigneeType);
        return n;
    }
}
