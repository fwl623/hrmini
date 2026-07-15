package com.company.hrms.module.employee.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 合同类型
 * DB: VARCHAR(16) FIXED / UNFIXED / LABOR
 * API JSON: 小写 snake_case
 */
public enum ContractType {

    FIXED("fixed"),
    UNFIXED("unfixed"),
    LABOR("labor");

    private final String apiValue;

    ContractType(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonValue
    public String getApiValue() {
        return apiValue;
    }

    public static ContractType fromDbValue(String dbValue) {
        return valueOf(dbValue.toUpperCase());
    }
}
