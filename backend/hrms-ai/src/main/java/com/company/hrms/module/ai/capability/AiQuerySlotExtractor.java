package com.company.hrms.module.ai.capability;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从用户话术中抽取部门名 / 查人关键词。
 */
public final class AiQuerySlotExtractor {

    private static final Pattern DEPT_PATTERNS = Pattern.compile(
            "(?:"
                    // 后端部门有哪些员工 / 后端部门的员工名单
                    + "([\u4e00-\u9fa5A-Za-z0-9]{2,20}?)(?:部门|部)(?:有哪些|有什么|的)?(?:员工|名单|人员|人|花名册)?"
                    + "|看(?:一下|看)?([\u4e00-\u9fa5A-Za-z0-9]{2,20}?)(?:的)?(?:员工|名单)"
                    + "|([\u4e00-\u9fa5A-Za-z0-9]{2,20}?)有多少人"
                    + "|([\u4e00-\u9fa5A-Za-z0-9]{2,20}?)(?:部门|部)?人数"
                    + ")"
    );

    private static final Pattern PERSON_PATTERNS = Pattern.compile(
            "(?:"
                    + "(?:查一下|找一下|查员工|查找|搜索)\\s*([\u4e00-\u9fa5A-Za-z0-9]{1,20})"
                    + "|工号\\s*([A-Za-z0-9_-]{1,32})"
                    + ")"
    );

    /** 整句像部门名：后端开发部 / 后端部门 / 短中文名 */
    private static final Pattern BARE_DEPT = Pattern.compile(
            "^[\u4e00-\u9fa5A-Za-z0-9]{2,24}(?:部门|部|组)?$"
    );

    private AiQuerySlotExtractor() {
    }

    public static String extractDeptHint(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String trimmed = message.trim();
        // 整句就是部门名时，保留「部/部门」后缀交给解析（精确匹配更好）
        if (isBareDeptUtterance(trimmed)) {
            return trimmed;
        }
        Matcher m = DEPT_PATTERNS.matcher(trimmed);
        if (m.find()) {
            for (int i = 1; i <= m.groupCount(); i++) {
                String g = m.group(i);
                if (g != null && !g.isBlank()) {
                    return cleanDept(g);
                }
            }
        }
        // 兜底：去掉套话后剩余短词
        String s = trimmed
                .replaceAll("我要|我想|请|帮我|看看|查看|打开|一下", "")
                .replaceAll("员工名单|部门员工|名单|有哪些员工|哪些员工|有哪些人|哪些人|员工|花名册|有多少人|多少人|人数|的", "")
                .trim();
        if (s.length() >= 2 && s.length() <= 24 && !s.contains(" ")) {
            return cleanDept(s);
        }
        return null;
    }

    /** 用户只回了一个部门名（多轮追问补槽）。 */
    public static boolean isBareDeptUtterance(String message) {
        if (message == null) {
            return false;
        }
        String s = message.trim();
        if (s.length() < 2 || s.length() > 24) {
            return false;
        }
        if (s.contains("？") || s.contains("?") || s.contains("，") || s.contains(",") || s.contains(" ")) {
            return false;
        }
        // 明显不是部门名的问句词
        if (s.contains("怎么") || s.contains("如何") || s.contains("请假") || s.contains("加班")
                || s.contains("审批") || s.contains("工资") || s.contains("打卡")) {
            return false;
        }
        return BARE_DEPT.matcher(s).matches();
    }

    public static String extractPersonKeyword(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        Matcher m = PERSON_PATTERNS.matcher(message);
        if (m.find()) {
            for (int i = 1; i <= m.groupCount(); i++) {
                String g = m.group(i);
                if (g != null && !g.isBlank()) {
                    return g.trim();
                }
            }
        }
        return null;
    }

    private static String cleanDept(String raw) {
        String s = raw.trim();
        s = s.replaceAll("(部门|部|组)$", "");
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.isBlank() || lower.equals("公司") || lower.equals("全部") || lower.equals("所有")) {
            return null;
        }
        return s.isBlank() ? null : s;
    }
}
