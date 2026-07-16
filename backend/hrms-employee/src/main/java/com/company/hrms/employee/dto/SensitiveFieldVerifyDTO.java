package com.company.hrms.employee.dto;

import lombok.Data;

/**
 * 敏感字段二次验证请求
 * POST /api/v1/employees/{id}/sensitive/{field}
 */
@Data
public class SensitiveFieldVerifyDTO {
    private String password;
}
