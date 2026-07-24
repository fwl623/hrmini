package com.company.hrms.module.ai.capability;

import com.company.hrms.common.roster.EmployeeBriefDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * 员工名单 INFO_LIST 卡片组装。
 */
public final class EmployeeRosterActionFactory {

    public static final String FORM_ID = "employee_roster";
    public static final String ACTION_TYPE = "INFO_LIST";
    public static final String ROUTE = "/admin/employee/list";

    private EmployeeRosterActionFactory() {
    }

    public static ObjectNode buildAction(
            ObjectMapper om,
            String intent,
            String label,
            List<EmployeeBriefDTO> items,
            long total,
            String deptName,
            String hint,
            Long departmentId) {
        ObjectNode action = om.createObjectNode();
        action.put("type", ACTION_TYPE);
        action.put("intent", intent == null ? "employee_list" : intent);
        action.put("label", label == null ? "员工名单" : label);
        action.put("formId", FORM_ID);
        String route = ROUTE;
        if (departmentId != null) {
            route = ROUTE + "?departmentIds=" + departmentId;
        }
        action.put("route", route);

        ObjectNode meta = action.putObject("meta");
        if (deptName != null) {
            meta.put("deptName", deptName);
            meta.put("title", deptName + "员工");
        } else {
            meta.put("title", label == null ? "员工名单" : label);
        }
        meta.put("total", total);
        if (hint != null) {
            meta.put("hint", hint);
        }

        ArrayNode arr = action.putArray("items");
        if (items != null) {
            for (EmployeeBriefDTO e : items) {
                if (e == null) {
                    continue;
                }
                ObjectNode row = arr.addObject();
                if (e.getEmployeeId() != null) {
                    row.put("employeeId", e.getEmployeeId());
                }
                if (e.getEmpNo() != null) {
                    row.put("empNo", e.getEmpNo());
                }
                if (e.getName() != null) {
                    row.put("name", e.getName());
                }
                if (e.getDepartment() != null) {
                    row.put("department", e.getDepartment());
                }
                if (e.getPosition() != null) {
                    row.put("position", e.getPosition());
                }
                if (e.getGrade() != null) {
                    row.put("grade", e.getGrade());
                }
                if (e.getEmploymentStatus() != null) {
                    row.put("employmentStatus", e.getEmploymentStatus());
                }
            }
        }
        return action;
    }
}
