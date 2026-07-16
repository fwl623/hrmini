package com.company.hrms.employee.vo;

import lombok.Data;

/**
 * 敏感字段响应（二次验证后返回明文）
 * GET /api/v1/employees/{id}/sensitive/{field}
 */
@Data
public class SensitiveFieldVO {
    private Long employeeId;
    private String field;
    private String value;
}
