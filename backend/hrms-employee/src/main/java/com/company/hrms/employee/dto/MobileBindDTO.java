package com.company.hrms.employee.dto;

import lombok.Data;

/**
 * 手机绑定
 * POST /api/v1/profile/security/mobile/bind
 */
@Data
public class MobileBindDTO {
    private String mobile;
    private String smsCode;
}
