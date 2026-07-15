package com.company.hrms.module.employee.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 员工离职申请状态（portal 端）
 * DB: VARCHAR(16) PENDING / APPROVED / REJECTED / CANCELLED
 * API JSON: 小写 snake_case
 */
public enum ResignationRequestStatus {

    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
    CANCELLED("cancelled");

    private final String apiValue;

    ResignationRequestStatus(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonValue
    public String getApiValue() {
        return apiValue;
    }

    public static ResignationRequestStatus fromDbValue(String dbValue) {
        return valueOf(dbValue.toUpperCase());
    }
}
