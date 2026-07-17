package com.company.hrms.employee.vo;

import lombok.Data;

/**
 * 敏感字段响应
 * <p>
 * 用于 GET /api/v1/employees/{id}/sensitive/{field} 接口。
 * 密码二次验证通过后，返回 AES-256-GCM 解密后的明文敏感数据。
 * 支持字段：idNumber（身份证号）、bankAccount（银行卡号）。
 * </p>
 */
@Data
public class SensitiveFieldVO {

    /** 员工ID */
    private Long employeeId;

    /** 敏感字段名（idNumber / bankAccount） */
    private String field;

    /** 解密后的明文值 */
    private String value;
}
