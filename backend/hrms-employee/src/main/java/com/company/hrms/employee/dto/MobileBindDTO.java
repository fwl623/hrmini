package com.company.hrms.employee.dto;

import lombok.Data;

/**
 * 手机绑定请求参数
 * <p>
 * POST /api/v1/profile/security/mobile/bind
 * 首次绑定手机号，需提供手机号及短信验证码。
 * 注：变更档案手机号不走此接口，请走手机号变更申请流程。
 * </p>
 */
@Data
public class MobileBindDTO {

    /** 手机号（11位） */
    private String mobile;

    /** 短信验证码 */
    private String smsCode;
}
