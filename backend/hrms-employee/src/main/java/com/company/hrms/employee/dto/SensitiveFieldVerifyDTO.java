package com.company.hrms.employee.dto;

import lombok.Data;

/**
 * 敏感字段二次验证请求参数
 * <p>
 * 用于 GET /api/v1/employees/{id}/sensitive/{field} 之前的身份验证。
 * 当前通过请求头 X-Sensitive-Password 传递密码，本 DTO 预留备用。
 * 验证通过后返回 AES-256-GCM 解密的明文敏感数据。
 * </p>
 */
@Data
public class SensitiveFieldVerifyDTO {

    /** 登录密码（二次验证用） */
    private String password;
}
