package com.company.hrms.module.ai.capability;

import com.company.hrms.common.leave.LeaveBalanceBriefDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.List;

/**
 * 本人假期余额 DATA_CARD。
 */
public final class LeaveBalanceActionFactory {

    public static final String FORM_ID = "leave_balance";
    public static final String ACTION_TYPE = "DATA_CARD";
    public static final String ROUTE = "/portal/leave";

    private LeaveBalanceActionFactory() {
    }

    public static ObjectNode buildAction(ObjectMapper om, List<LeaveBalanceBriefDTO> balances) {
        ObjectNode action = om.createObjectNode();
        action.put("type", ACTION_TYPE);
        action.put("intent", "leave_balance");
        action.put("formId", FORM_ID);
        action.put("label", "我的假期余额");
        action.put("route", ROUTE);
        action.put("hint", "余额为当前可用额度（含预扣后剩余）；申请请假请说「我要请假」。");
        ArrayNode stats = action.putArray("stats");
        if (balances != null) {
            for (LeaveBalanceBriefDTO b : balances) {
                if (b == null) {
                    continue;
                }
                ObjectNode s = stats.addObject();
                s.put("label", b.getLabel() != null ? b.getLabel() : b.getLeaveType());
                BigDecimal bal = b.getBalance() != null ? b.getBalance() : BigDecimal.ZERO;
                // 去掉多余尾零，便于展示 3 / 3.5
                s.put("value", bal.stripTrailingZeros().toPlainString());
            }
        }
        if (stats.isEmpty()) {
            ObjectNode annual = stats.addObject();
            annual.put("label", "年假剩余(天)");
            annual.put("value", "0");
            ObjectNode comp = stats.addObject();
            comp.put("label", "调休剩余(天)");
            comp.put("value", "0");
        }
        return action;
    }

    public static ObjectNode buildNeedBindEmployee(ObjectMapper om) {
        ObjectNode action = om.createObjectNode();
        action.put("type", ACTION_TYPE);
        action.put("intent", "leave_balance");
        action.put("formId", FORM_ID);
        action.put("label", "我的假期余额");
        action.put("route", ROUTE);
        action.put("hint", "当前账号未绑定员工档案，无法查询假期余额。请联系 HR 确认入职开号。");
        action.putArray("stats");
        return action;
    }
}
