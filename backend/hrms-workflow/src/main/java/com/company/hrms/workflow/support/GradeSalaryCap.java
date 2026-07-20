package com.company.hrms.workflow.support;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按职位职级上限估算薪资封顶（无独立职级薪资表时的 PRD 二审阈值）。
 */
public final class GradeSalaryCap {

    private static final BigDecimal DEFAULT = new BigDecimal("20000");
    private static final Pattern DIGITS = Pattern.compile("(\\d+)");

    private GradeSalaryCap() {
    }

    public static BigDecimal ofRankMax(String rankMax) {
        if (rankMax == null || rankMax.isBlank()) {
            return DEFAULT;
        }
        String r = rankMax.trim().toUpperCase();
        char seq = Character.isLetter(r.charAt(0)) ? r.charAt(0) : 'P';
        Matcher m = DIGITS.matcher(r);
        int level = m.find() ? Integer.parseInt(m.group(1)) : 5;
        if (level < 1) {
            level = 1;
        }
        long base = switch (seq) {
            case 'M' -> 15_000L;
            case 'S' -> 10_000L;
            default -> 8_000L;
        };
        long step = switch (seq) {
            case 'M' -> 5_000L;
            case 'S' -> 3_000L;
            default -> 4_000L;
        };
        return BigDecimal.valueOf(base + (long) (level - 1) * step);
    }
}
