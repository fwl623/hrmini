package com.company.hrms.module.ai.capability;

import com.company.hrms.common.org.DeptBriefDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * 部门人数 DATA_CARD 组装。
 */
public final class DeptStatsActionFactory {

    public static final String FORM_ID = "dept_stats";
    public static final String ACTION_TYPE = "DATA_CARD";
    public static final String ROUTE_ORG = "/admin/org/departments";
    public static final String ROUTE_EMP = "/admin/employee/list";

    private DeptStatsActionFactory() {
    }

    public static ObjectNode buildDeptHeadcount(ObjectMapper om, String intent, DeptBriefDTO dept) {
        ObjectNode action = om.createObjectNode();
        action.put("type", ACTION_TYPE);
        action.put("intent", intent == null ? "dept_headcount" : intent);
        action.put("formId", FORM_ID);
        String name = dept != null && dept.getName() != null ? dept.getName() : "该部门";
        action.put("label", name + "人数");
        action.put("route", ROUTE_EMP);
        if (dept != null && dept.getDepartmentId() != null) {
            action.put("route", ROUTE_EMP + "?departmentIds=" + dept.getDepartmentId());
        }
        ArrayNode stats = action.putArray("stats");
        addStat(stats, "直属在职", dept == null || dept.getHeadcount() == null ? 0 : dept.getHeadcount());
        addStat(stats, "含下级",
                dept == null || dept.getHeadcountIncludingSub() == null
                        ? 0
                        : dept.getHeadcountIncludingSub());
        return action;
    }

    public static ObjectNode buildOrgOverview(ObjectMapper om, List<DeptBriefDTO> depts) {
        ObjectNode action = om.createObjectNode();
        action.put("type", ACTION_TYPE);
        action.put("intent", "org_overview");
        action.put("formId", FORM_ID);
        action.put("label", "各部门人数");
        action.put("route", ROUTE_ORG);
        ArrayNode stats = action.putArray("stats");
        int company = 0;
        if (depts != null) {
            for (DeptBriefDTO d : depts) {
                if (d == null) {
                    continue;
                }
                int hc = d.getHeadcount() == null ? 0 : d.getHeadcount();
                company += hc;
                String label = d.getName() == null ? ("部门#" + d.getDepartmentId()) : d.getName();
                addStat(stats, label, hc);
            }
        }
        // 公司合计放最前
        ArrayNode withTotal = om.createArrayNode();
        ObjectNode total = om.createObjectNode();
        total.put("label", "直属合计(列表内)");
        total.put("value", company);
        withTotal.add(total);
        withTotal.addAll(stats);
        action.set("stats", withTotal);
        return action;
    }

    public static ObjectNode buildAmbiguousDepts(ObjectMapper om, List<DeptBriefDTO> candidates) {
        ObjectNode action = om.createObjectNode();
        action.put("type", ACTION_TYPE);
        action.put("intent", "employee_list");
        action.put("formId", FORM_ID);
        action.put("label", "请确认部门");
        action.put("route", ROUTE_ORG);
        action.put("hint", "匹配到多个部门，请再说一次完整部门名，例如「后端部门的员工名单」");
        ArrayNode stats = action.putArray("stats");
        if (candidates != null) {
            for (DeptBriefDTO d : candidates) {
                if (d == null) {
                    continue;
                }
                String label = d.getName() == null ? ("#" + d.getDepartmentId()) : d.getName();
                int hc = d.getHeadcount() == null ? 0 : d.getHeadcount();
                addStat(stats, label, hc);
            }
        }
        return action;
    }

    private static void addStat(ArrayNode stats, String label, int value) {
        ObjectNode s = stats.addObject();
        s.put("label", label);
        s.put("value", value);
    }
}
