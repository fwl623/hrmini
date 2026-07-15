package com.company.hrms.module.employee.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 性别
 * DB: VARCHAR(8) MALE / FEMALE
 * API JSON: 小写 snake_case
 */
public enum Gender {

    MALE("male"),
    FEMALE("female");

    private final String apiValue;

    Gender(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonValue
    public String getApiValue() {
        return apiValue;
    }

    public static Gender fromDbCode(String dbValue) {
        return valueOf(dbValue.toUpperCase());
    }

    public static Gender fromApiValue(String apiValue) {
        for (Gender g : values()) {
            if (g.apiValue.equals(apiValue)) return g;
        }
        throw new IllegalArgumentException("Unknown Gender: " + apiValue);
    }
}
