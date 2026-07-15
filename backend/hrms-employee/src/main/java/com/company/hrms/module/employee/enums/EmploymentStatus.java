package com.company.hrms.module.employee.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 在职状态
 * DB: TINYINT (10=试用期 20=正式 30=待离职 40=已离职)
 * API JSON: 小写 snake_case
 */
public enum EmploymentStatus {

    PROBATION(10, "probation"),
    REGULAR(20, "regular"),
    PENDING_RESIGN(30, "pending_resign"),
    RESIGNED(40, "resigned");

    private final int dbCode;
    private final String apiValue;

    EmploymentStatus(int dbCode, String apiValue) {
        this.dbCode = dbCode;
        this.apiValue = apiValue;
    }

    public int getDbCode() {
        return dbCode;
    }

    @JsonValue
    public String getApiValue() {
        return apiValue;
    }

    public static EmploymentStatus fromDbCode(int dbCode) {
        for (EmploymentStatus s : values()) {
            if (s.dbCode == dbCode) return s;
        }
        throw new IllegalArgumentException("Unknown EmploymentStatus dbCode: " + dbCode);
    }

    public static EmploymentStatus fromApiValue(String apiValue) {
        for (EmploymentStatus s : values()) {
            if (s.apiValue.equals(apiValue)) return s;
        }
        throw new IllegalArgumentException("Unknown EmploymentStatus apiValue: " + apiValue);
    }
}
