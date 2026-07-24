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
 * 加班办事：表单 schema + 轻量槽位抽取。
 */
public final class OvertimeBizActionFactory {

    public static final String FORM_ID = "overtime_apply";
    public static final String ACTION_TYPE = "FORM_SUBMIT";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private OvertimeBizActionFactory() {
    }

    public static ObjectNode buildAction(ObjectMapper om, String userMessage, String route) {
        ObjectNode action = om.createObjectNode();
        action.put("type", ACTION_TYPE);
        action.put("intent", "overtime");
        action.put("label", "确认加班");
        action.put("formId", FORM_ID);
        if (route != null) {
            action.put("route", route);
        }
        ObjectNode submitApi = action.putObject("submitApi");
        submitApi.put("method", "POST");
        submitApi.put("path", "/api/v1/overtime/applications");

        ArrayNode schema = action.putArray("formSchema");
        addField(schema, "overtimeDate", "加班日期", "date", true);
        addField(schema, "startTime", "开始时间", "datetime", true);
        addField(schema, "endTime", "结束时间", "datetime", true);
        addField(schema, "reason", "加班原因", "textarea", true);

        ObjectNode prefill = extractPrefill(om, userMessage);
        if (prefill.size() > 0) {
            action.set("prefill", prefill);
        }
        return action;
    }

    public static ObjectNode extractPrefill(ObjectMapper om, String message) {
        ObjectNode prefill = om.createObjectNode();
        if (message == null || message.isBlank()) {
            return prefill;
        }
        String q = message.toLowerCase(Locale.ROOT);
        LocalDate day = null;
        LocalDate today = LocalDate.now();
        if (q.contains("大后天")) {
            day = today.plusDays(3);
        } else if (q.contains("后天")) {
            day = today.plusDays(2);
        } else if (q.contains("明天")) {
            day = today.plusDays(1);
        } else if (q.contains("今天") || q.contains("今晚")) {
            day = today;
        }

        int hours = 2;
        Matcher hm = Pattern.compile("(\\d+(?:\\.5)?)\\s*小时").matcher(q);
        if (hm.find()) {
            try {
                hours = Math.max(1, (int) Math.ceil(Double.parseDouble(hm.group(1))));
            } catch (NumberFormatException ignored) {
                hours = 2;
            }
        }

        if (day != null) {
            LocalDateTime start = LocalDateTime.of(day, LocalTime.of(18, 0));
            LocalDateTime end = start.plusHours(hours);
            prefill.put("overtimeDate", day.format(DAY));
            prefill.put("startTime", start.format(ISO));
            prefill.put("endTime", end.format(ISO));
        }
        return prefill;
    }

    private static void addField(ArrayNode schema, String name, String label, String fieldType, boolean required) {
        ObjectNode f = schema.addObject();
        f.put("name", name);
        f.put("label", label);
        f.put("fieldType", fieldType);
        f.put("required", required);
    }
}
