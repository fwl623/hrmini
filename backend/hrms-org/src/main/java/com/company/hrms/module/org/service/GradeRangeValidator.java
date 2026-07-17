package com.company.hrms.module.org.service;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 职位序列职级校验：M1-M5 / P1-P10 / S1-S5。
 */
public final class GradeRangeValidator {

    private static final Map<String, Integer> M_RANKS = ranks("M", 5);
    private static final Map<String, Integer> P_RANKS = ranks("P", 10);
    private static final Map<String, Integer> S_RANKS = ranks("S", 5);

    private GradeRangeValidator() {
    }

    public static void validate(String sequenceCode, String gradeMin, String gradeMax) {
        Map<String, Integer> ranks = ranksOf(sequenceCode);
        Integer minIdx = ranks.get(gradeMin);
        Integer maxIdx = ranks.get(gradeMax);
        if (minIdx == null || maxIdx == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "职级须在序列合法范围内（M1-M5 / P1-P10 / S1-S5）");
        }
        if (minIdx > maxIdx) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "职级下限不能高于上限");
        }
    }

    /** 判断职级是否落在 [gradeMin, gradeMax]（含端点） */
    public static boolean inRange(String sequenceCode, String grade, String gradeMin, String gradeMax) {
        if (grade == null || grade.isBlank()) {
            return true;
        }
        Map<String, Integer> ranks = ranksOf(sequenceCode);
        Integer g = ranks.get(grade.trim().toUpperCase());
        Integer minIdx = ranks.get(gradeMin);
        Integer maxIdx = ranks.get(gradeMax);
        if (g == null || minIdx == null || maxIdx == null) {
            return false;
        }
        return g >= minIdx && g <= maxIdx;
    }

    private static Map<String, Integer> ranksOf(String sequenceCode) {
        return switch (sequenceCode) {
            case "M" -> M_RANKS;
            case "P" -> P_RANKS;
            case "S" -> S_RANKS;
            default -> throw new BusinessException(ErrorCode.PARAM_INVALID, "职位序列须为 M/P/S");
        };
    }

    private static Map<String, Integer> ranks(String prefix, int max) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 1; i <= max; i++) {
            map.put(prefix + i, i);
        }
        return map;
    }
}
