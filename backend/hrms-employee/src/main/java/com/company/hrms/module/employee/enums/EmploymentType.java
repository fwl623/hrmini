package com.company.hrms.module.employee.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 用工类型
 * DB: VARCHAR(16) fulltime / parttime / intern
 * API JSON: 小写 snake_case
 */
public enum EmploymentType {

    FULLTIME("fulltime"),
    PARTTIME("parttime"),
    INTERN("intern");

    private final String apiValue;

    EmploymentType(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonValue
    public String getApiValue() {
        return apiValue;
    }

    public static EmploymentType fromDbValue(String dbValue) {
        return valueOf(dbValue.toUpperCase());
    }

    public static EmploymentType fromApiValue(String apiValue) {
        for (EmploymentType t : values()) {
            if (t.apiValue.equals(apiValue)) return t;
        }
        throw new IllegalArgumentException("Unknown EmploymentType: " + apiValue);
    }
}
