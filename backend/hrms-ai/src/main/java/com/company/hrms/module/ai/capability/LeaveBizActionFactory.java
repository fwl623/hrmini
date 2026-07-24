package com.company.hrms.module.ai.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 请假办事：表单 schema + 轻量槽位抽取（明天/后天/N天/假种）。
 */
public final class LeaveBizActionFactory {

    public static final String FORM_ID = "leave_apply";
    public static final String ACTION_TYPE = "FORM_SUBMIT";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private LeaveBizActionFactory() {
    }

    public static ObjectNode buildAction(ObjectMapper om, String userMessage, String route) {
        ObjectNode action = om.createObjectNode();
        action.put("type", ACTION_TYPE);
        action.put("intent", "leave");
        action.put("label", "确认请假");
        action.put("formId", FORM_ID);
        if (route != null) {
            action.put("route", route);
        }
        ObjectNode submitApi = action.putObject("submitApi");
        submitApi.put("method", "POST");
        submitApi.put("path", "/api/v1/leaves/applications");

        ArrayNode schema = action.putArray("formSchema");
        addField(schema, "leaveType", "请假类型", "select", true,
                options(om,
                        opt(om, "ANNUAL", "年假"),
                        opt(om, "SICK", "病假"),
                        opt(om, "PERSONAL", "事假"),
                        opt(om, "MARRIAGE", "婚假"),
                        opt(om, "MATERNITY", "产假"),
                        opt(om, "BEREAVEMENT", "丧假"),
                        opt(om, "COMP_OFF", "调休")));
        addField(schema, "startTime", "开始时间", "datetime", true, null);
        addField(schema, "endTime", "结束时间", "datetime", true, null);
        addField(schema, "days", "请假天数", "number", true, null);
        addField(schema, "reason", "请假原因", "textarea", false, null);
        addField(schema, "attachment", "证明材料（可选）", "text", false, null);

        ObjectNode prefill = extractPrefill(om, userMessage);
        if (prefill.size() > 0) {
            action.set("prefill", prefill);
        }
        return action;
    }

    /** 从自然语言中尽量抽出假种与起止时间。 */
    public static ObjectNode extractPrefill(ObjectMapper om, String message) {
        ObjectNode prefill = om.createObjectNode();
        if (message == null || message.isBlank()) {
            return prefill;
        }
        String q = message.toLowerCase(Locale.ROOT);

        String leaveType = null;
        if (q.contains("年假")) leaveType = "ANNUAL";
        else if (q.contains("病假") || q.contains("生病") || q.contains("不舒服")
                || q.contains("感冒") || q.contains("发烧") || q.contains("身体不适")) {
            leaveType = "SICK";
        } else if (q.contains("事假")) leaveType = "PERSONAL";
        else if (q.contains("婚假")) leaveType = "MARRIAGE";
        else if (q.contains("产假")) leaveType = "MATERNITY";
        else if (q.contains("丧假")) leaveType = "BEREAVEMENT";
        else if (q.contains("调休")) leaveType = "COMP_OFF";
        if (leaveType != null) {
            prefill.put("leaveType", leaveType);
        }

        LocalDate startDate = null;
        LocalDate today = LocalDate.now();
        if (q.contains("大后天")) {
            startDate = today.plusDays(3);
        } else if (q.contains("后天")) {
            startDate = today.plusDays(2);
        } else if (q.contains("明天")) {
            startDate = today.plusDays(1);
        } else if (q.contains("今天")) {
            startDate = today;
        }

        double days = 1;
        boolean hasDays = false;
        Matcher dayMatcher = Pattern.compile("(\\d+(?:\\.5)?)\\s*天").matcher(q);
        if (dayMatcher.find()) {
            try {
                days = Double.parseDouble(dayMatcher.group(1));
                if (days <= 0) {
                    days = 1;
                }
                hasDays = true;
            } catch (NumberFormatException ignored) {
                days = 1;
            }
        }

        if (startDate != null) {
            LocalDateTime start = LocalDateTime.of(startDate, LocalTime.of(9, 0));
            long daySpan = Math.max(1, (long) Math.ceil(days)) - 1;
            LocalDateTime end = LocalDateTime.of(startDate.plusDays(daySpan), LocalTime.of(18, 0));
            if (days < 1) {
                end = LocalDateTime.of(startDate, LocalTime.of(13, 0));
            }
            prefill.put("startTime", start.format(ISO));
            prefill.put("endTime", end.format(ISO));
            prefill.put("days", days);
        } else if (hasDays) {
            prefill.put("days", days);
        }

        return prefill;
    }

    private static void addField(ArrayNode schema, String name, String label, String fieldType,
                                 boolean required, ArrayNode options) {
        ObjectNode f = schema.addObject();
        f.put("name", name);
        f.put("label", label);
        f.put("fieldType", fieldType);
        f.put("required", required);
        if (options != null) {
            f.set("options", options);
        }
    }

    private static ArrayNode options(ObjectMapper om, ObjectNode... opts) {
        ArrayNode arr = om.createArrayNode();
        for (ObjectNode o : opts) {
            arr.add(o);
        }
        return arr;
    }

    private static ObjectNode opt(ObjectMapper om, String value, String label) {
        ObjectNode o = om.createObjectNode();
        o.put("value", value);
        o.put("label", label);
        return o;
    }
}
