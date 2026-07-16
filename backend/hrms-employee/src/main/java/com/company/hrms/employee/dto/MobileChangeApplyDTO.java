package com.company.hrms.employee.dto;

import lombok.Data;

/**
 * 手机号变更申请
 * POST /api/v1/profile/mobile-change-applications
 */
@Data
public class MobileChangeApplyDTO {
    private String newMobile;
    private String smsCode;
    private String reason;
}
