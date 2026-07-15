package com.company.hrms.module.employee.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 手机号变更申请状态
 * DB: VARCHAR(16) PENDING / APPROVED / REJECTED / CANCELLED
 * API JSON: 小写 snake_case
 */
public enum MobileChangeStatus {

    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
    CANCELLED("cancelled");

    private final String apiValue;

    MobileChangeStatus(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonValue
    public String getApiValue() {
        return apiValue;
    }

    public static MobileChangeStatus fromDbValue(String dbValue) {
        return valueOf(dbValue.toUpperCase());
    }
}
